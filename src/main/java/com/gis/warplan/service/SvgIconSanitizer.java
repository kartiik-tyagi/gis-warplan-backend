package com.gis.warplan.service;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.gis.warplan.web.BadRequestException;

/**
 * Turns a drawn SVG into a small, theme-token document. Elements and attributes outside the icon
 * vocabulary are rejected, so a saved file can be inlined in the page without carrying scripts.
 */
public final class SvgIconSanitizer {

    static final int MAX_CHARS = 512 * 1024;

    private static final int MAX_NODES = 500;
    private static final int MAX_DEPTH = 16;
    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    private static final Set<String> TOKENS = Set.of(
            "primary", "primaryContrast", "secondary", "secondaryContrast",
            "backgroundDefault", "backgroundPaper",
            "textPrimary", "textSecondary", "textDisabled",
            "divider",
            "error", "errorContrast", "warning", "warningContrast",
            "success", "successContrast", "info", "infoContrast");

    private static final Set<String> SHAPES = Set.of(
            "path", "rect", "circle", "ellipse", "line", "polyline", "polygon", "text");

    private static final Set<String> COMMON = Set.of(
            "id", "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-dasharray",
            "opacity", "transform", "fill-rule", "data-wp-fill", "data-wp-stroke");

    private static final Pattern NUMBER = Pattern.compile("-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");
    private static final Pattern HEX = Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    private static final Pattern RGB = Pattern.compile("rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*(?:,\\s*(0|1|0?\\.\\d+)\\s*)?\\)");
    private static final Pattern VAR = Pattern.compile("var\\(\\s*--wp-([A-Za-z]+)\\s*\\)");
    private static final Pattern PATH_D = Pattern.compile("[MmLlHhVvCcSsQqTtAaZz0-9eE,.\\s+-]+");
    private static final Pattern TRANSFORM = Pattern.compile(
            "^(?:\\s*(?:matrix|translate|scale|rotate|skewX|skewY)\\(\\s*-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?(?:\\s*[, ]\\s*-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)*\\s*\\))+\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ID = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,40}");
    private static final Pattern FONT_FAMILY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ,.'\"-]{0,79}");
    private static final Pattern ASPECT = Pattern.compile("none|x(Min|Mid|Max)Y(Min|Mid|Max)( (meet|slice))?");

    private SvgIconSanitizer() {
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Icon file is empty.");
        }
        String text = raw.charAt(0) == '\uFEFF' ? raw.substring(1) : raw;
        if (text.indexOf('\0') >= 0 || text.length() > MAX_CHARS) {
            throw bad();
        }
        Document source = parse(text);
        Element root = source.getDocumentElement();
        if (root == null || !"svg".equals(local(root))) {
            throw bad();
        }
        Document document = newDocument();
        int[] nodes = {0};
        Element svg = rebuild(root, document, 0, nodes);
        if (!svg.hasAttribute("viewBox")) {
            svg.setAttribute("viewBox", "0 0 24 24");
        }
        if (!svg.hasAttribute("width")) {
            svg.setAttribute("width", "24");
        }
        if (!svg.hasAttribute("height")) {
            svg.setAttribute("height", "24");
        }
        svg.setAttribute("xmlns", SVG_NS);
        document.appendChild(svg);
        return write(document);
    }

    private static Element rebuild(Element source, Document document, int depth, int[] nodes) {
        if (depth > MAX_DEPTH) {
            throw bad();
        }
        String name = local(source);
        if (!allowedElement(name)) {
            throw bad();
        }
        nodes[0]++;
        if (nodes[0] > MAX_NODES) {
            throw bad();
        }
        rejectDangerousAttributes(source);
        Element copy = document.createElement(name);
        Set<String> allowed = allowedAttributes(name);
        NamedNodeMap attributes = source.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String attributeName = local(attribute);
            if (attributeName == null || attributeName.isBlank() || "xmlns".equals(attributeName) || attributeName.startsWith("xmlns:")) {
                continue;
            }
            if (!allowed.contains(attributeName)) {
                continue;
            }
            String value = attribute.getNodeValue();
            if (value == null) {
                continue;
            }
            if (value.length() > 8000) {
                throw bad();
            }
            copy.setAttribute(attributeName, validateAttribute(attributeName, value.trim()));
        }
        reconcilePaint(copy, "fill", "data-wp-fill");
        reconcilePaint(copy, "stroke", "data-wp-stroke");
        Node child = source.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String childName = local(child);
                if (!acceptsChild(name, childName)) {
                    throw bad();
                }
                copy.appendChild(rebuild((Element) child, document, depth + 1, nodes));
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String content = child.getTextContent();
                if (content != null && !content.isBlank()) {
                    if (!isTextContainer(name)) {
                        throw bad();
                    }
                    copy.appendChild(document.createTextNode(cleanText(content)));
                }
            } else if (child.getNodeType() == Node.COMMENT_NODE) {
                // dropped
            } else if (child.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                throw bad();
            }
            child = child.getNextSibling();
        }
        requireGeometry(copy);
        return copy;
    }

    private static boolean allowedElement(String name) {
        return "svg".equals(name) || "g".equals(name) || "title".equals(name) || "desc".equals(name)
                || "tspan".equals(name) || SHAPES.contains(name);
    }

    private static boolean acceptsChild(String parent, String child) {
        if ("title".equals(child) || "desc".equals(child)) {
            return !"title".equals(parent) && !"desc".equals(parent) && !"tspan".equals(parent);
        }
        if ("tspan".equals(child)) {
            return "text".equals(parent) || "tspan".equals(parent);
        }
        if ("svg".equals(parent) || "g".equals(parent)) {
            return "g".equals(child) || "text".equals(child) || SHAPES.contains(child);
        }
        return false;
    }

    private static boolean isTextContainer(String name) {
        return "text".equals(name) || "tspan".equals(name) || "title".equals(name) || "desc".equals(name);
    }

    private static Set<String> allowedAttributes(String name) {
        return switch (name) {
            case "svg" -> Set.of("width", "height", "viewBox", "preserveAspectRatio");
            case "rect" -> union("x", "y", "width", "height", "rx", "ry");
            case "circle" -> union("cx", "cy", "r");
            case "ellipse" -> union("cx", "cy", "rx", "ry");
            case "line" -> union("x1", "y1", "x2", "y2");
            case "polyline", "polygon" -> union("points");
            case "path" -> union("d");
            case "text", "tspan" -> union("x", "y", "dx", "dy", "font-size", "font-family", "font-weight", "text-anchor");
            case "g" -> COMMON;
            case "title", "desc" -> Set.of("id");
            default -> Set.of();
        };
    }

    private static Set<String> union(String... extra) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(COMMON);
        names.addAll(java.util.Arrays.asList(extra));
        return names;
    }

    private static void rejectDangerousAttributes(Element source) {
        NamedNodeMap attributes = source.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            String name = local(attributes.item(i)).toLowerCase(Locale.ROOT);
            if (name.startsWith("on") || "href".equals(name) || "src".equals(name) || "style".equals(name)) {
                throw bad();
            }
        }
    }

    private static String validateAttribute(String name, String value) {
        return switch (name) {
            case "fill", "stroke" -> canonicalPaint(value);
            case "data-wp-fill", "data-wp-stroke" -> {
                if (!isRole(value)) {
                    throw bad();
                }
                yield value;
            }
            case "id" -> token(value, ID);
            case "viewBox" -> viewBox(value);
            case "preserveAspectRatio" -> token(value, ASPECT);
            case "width", "height", "rx", "ry", "r", "stroke-width", "font-size" -> length(value, 0, 10000);
            case "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "dx", "dy" -> length(value, -10000, 10000);
            case "opacity" -> length(value, 0, 1);
            case "stroke-linecap" -> token(value, Pattern.compile("butt|round|square"));
            case "stroke-linejoin" -> token(value, Pattern.compile("miter|round|bevel"));
            case "stroke-dasharray" -> dash(value);
            case "fill-rule" -> token(value, Pattern.compile("nonzero|evenodd"));
            case "transform" -> {
                if (value.length() > 400 || !TRANSFORM.matcher(value).matches()) {
                    throw bad();
                }
                yield value.trim().replaceAll("\\s+", " ");
            }
            case "points" -> points(value);
            case "d" -> path(value);
            case "font-family" -> fontFamily(value);
            case "font-weight" -> token(value, Pattern.compile("normal|bold|[1-9]00"));
            case "text-anchor" -> token(value, Pattern.compile("start|middle|end"));
            default -> throw bad();
        };
    }

    private static void requireGeometry(Element element) {
        String name = element.getTagName();
        switch (name) {
            case "rect" -> {
                require(element, "width");
                require(element, "height");
                defaultZero(element, "x");
                defaultZero(element, "y");
            }
            case "circle" -> {
                require(element, "r");
                defaultZero(element, "cx");
                defaultZero(element, "cy");
            }
            case "ellipse" -> {
                require(element, "rx");
                require(element, "ry");
                defaultZero(element, "cx");
                defaultZero(element, "cy");
            }
            case "line" -> {
                require(element, "x1");
                require(element, "y1");
                require(element, "x2");
                require(element, "y2");
            }
            case "path" -> require(element, "d");
            case "polygon", "polyline" -> require(element, "points");
            case "text", "tspan" -> {
                defaultZero(element, "x");
                defaultZero(element, "y");
            }
            default -> {
            }
        }
    }

    private static void require(Element element, String name) {
        if (!element.hasAttribute(name)) {
            throw bad();
        }
    }

    private static void defaultZero(Element element, String name) {
        if (!element.hasAttribute(name)) {
            element.setAttribute(name, "0");
        }
    }

    private static void reconcilePaint(Element element, String attribute, String roleAttribute) {
        String role = element.getAttribute(roleAttribute);
        String paint = element.getAttribute(attribute);
        boolean hasRole = role != null && !role.isBlank();
        boolean hasPaint = paint != null && !paint.isBlank();
        if (!hasRole && !hasPaint) {
            return;
        }
        if (hasRole && !isRole(role)) {
            throw bad();
        }
        if ("locked".equals(role)) {
            if (!hasPaint || paint.startsWith("var(") || "currentColor".equals(paint)) {
                throw bad();
            }
            element.setAttribute(attribute, paint);
            element.setAttribute(roleAttribute, "locked");
            return;
        }
        if ("current".equals(role)) {
            element.setAttribute(attribute, "currentColor");
            element.setAttribute(roleAttribute, "current");
            return;
        }
        if (hasRole) {
            element.setAttribute(attribute, "var(--wp-" + role + ")");
            element.setAttribute(roleAttribute, role);
            return;
        }
        if (paint.startsWith("var(--wp-") && paint.endsWith(")")) {
            String token = paint.substring("var(--wp-".length(), paint.length() - 1);
            element.setAttribute(roleAttribute, token);
            return;
        }
        if ("currentColor".equals(paint)) {
            element.setAttribute(roleAttribute, "current");
            return;
        }
        element.setAttribute(roleAttribute, "locked");
    }

    private static boolean isRole(String role) {
        return "locked".equals(role) || "current".equals(role) || TOKENS.contains(role);
    }

    private static String canonicalPaint(String raw) {
        String value = raw.trim();
        if (value.equalsIgnoreCase("none")) {
            return "none";
        }
        if (value.equalsIgnoreCase("transparent")) {
            return "transparent";
        }
        if (value.equalsIgnoreCase("currentcolor")) {
            return "currentColor";
        }
        if (HEX.matcher(value).matches()) {
            return value.toLowerCase(Locale.ROOT);
        }
        Matcher variable = VAR.matcher(value);
        if (variable.matches()) {
            String token = variable.group(1);
            if (!TOKENS.contains(token)) {
                throw bad();
            }
            return "var(--wp-" + token + ")";
        }
        Matcher rgb = RGB.matcher(value);
        if (rgb.matches()) {
            int red = channel(rgb.group(1));
            int green = channel(rgb.group(2));
            int blue = channel(rgb.group(3));
            if (rgb.group(4) == null) {
                return "rgb(" + red + "," + green + "," + blue + ")";
            }
            return "rgba(" + red + "," + green + "," + blue + "," + rgb.group(4) + ")";
        }
        throw bad();
    }

    private static int channel(String raw) {
        int value = Integer.parseInt(raw);
        if (value > 255) {
            throw bad();
        }
        return value;
    }

    private static String viewBox(String value) {
        String[] parts = value.trim().split("[\\s,]+");
        if (parts.length != 4) {
            throw bad();
        }
        double x = number(parts[0], -10000, 10000);
        double y = number(parts[1], -10000, 10000);
        double width = number(parts[2], 0.01, 10000);
        double height = number(parts[3], 0.01, 10000);
        return trimNumber(x) + " " + trimNumber(y) + " " + trimNumber(width) + " " + trimNumber(height);
    }

    private static String points(String value) {
        if (value.length() > 8000) {
            throw bad();
        }
        String[] parts = value.trim().replace(',', ' ').split("\\s+");
        if (parts.length < 4 || parts.length % 2 != 0 || parts.length > 800) {
            throw bad();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i += 2) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(trimNumber(number(parts[i], -10000, 10000)))
                    .append(',')
                    .append(trimNumber(number(parts[i + 1], -10000, 10000)));
        }
        return builder.toString();
    }

    private static String path(String value) {
        String text = value.trim().replaceAll("\\s+", " ");
        if (text.length() > 8000 || !PATH_D.matcher(text).matches() || !text.matches(".*[MmLlHhVvCcSsQqTtAaZz].*")) {
            throw bad();
        }
        return text;
    }

    private static String dash(String value) {
        String text = value.trim();
        if (text.equalsIgnoreCase("none")) {
            return "none";
        }
        if (text.length() > 200) {
            throw bad();
        }
        String[] parts = text.split("[,\\s]+");
        if (parts.length == 0) {
            throw bad();
        }
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(trimNumber(number(part, 0, 10000)));
        }
        if (builder.length() == 0) {
            throw bad();
        }
        return builder.toString();
    }

    private static String fontFamily(String value) {
        String text = value.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("url") || text.indexOf('(') >= 0 || text.indexOf(')') >= 0 || text.indexOf(';') >= 0
                || text.indexOf('\\') >= 0 || text.indexOf('<') >= 0 || !FONT_FAMILY.matcher(text).matches()) {
            throw bad();
        }
        return text;
    }

    private static String token(String value, Pattern pattern) {
        if (!pattern.matcher(value).matches()) {
            throw bad();
        }
        return value;
    }

    private static String length(String raw, double min, double max) {
        return trimNumber(number(raw, min, max));
    }

    private static double number(String raw, double min, double max) {
        String text = raw.trim();
        if (text.endsWith("px")) {
            text = text.substring(0, text.length() - 2).trim();
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        if (!NUMBER.matcher(text).matches()) {
            throw bad();
        }
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            throw bad();
        }
        if (!Double.isFinite(value) || value < min || value > max) {
            throw bad();
        }
        return value;
    }

    private static String trimNumber(double value) {
        if (value == (long) value && Math.abs(value) < 1_000_000_000_000L) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String cleanText(String value) {
        if (value.length() > 200 || value.indexOf('\0') >= 0) {
            throw bad();
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\t' || c == '\r' || c >= ' ') {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String local(Node node) {
        String local = node.getLocalName();
        if (local != null && !local.isBlank()) {
            return local;
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static Document parse(String text) {
        try {
            DocumentBuilder builder = factory().newDocumentBuilder();
            InputSource source = new InputSource(new StringReader(text));
            source.setEncoding("UTF-8");
            return builder.parse(source);
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw bad();
        }
    }

    private static Document newDocument() {
        try {
            return factory().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException exception) {
            throw new IllegalStateException("SVG parser could not be created", exception);
        }
    }

    private static DocumentBuilderFactory factory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        try {
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException exception) {
            throw new IllegalStateException("SVG parser could not be secured", exception);
        }
        return factory;
    }

    private static String write(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            String text = writer.toString().trim();
            if (!text.contains("<svg") || text.length() > MAX_CHARS) {
                throw bad();
            }
            return text;
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("SVG could not be stored", exception);
        }
    }

    private static BadRequestException bad() {
        return new BadRequestException("SVG content is not allowed.");
    }
}
