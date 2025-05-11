package com.Burp2Jmx;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.Base64;
import java.util.regex.*;

public class Burp2Jmx {

    public static void main(String[] args) {
        /*
        if (args.length < 4) {
            System.out.println("Usage: java JMeterBurpGenerator <input_jmx> <burp_dump> <controller_name> <output_jmx>");
            return;
        }

        String inputJmxPath = args[0];
        String burpDumpPath = args[1];
        String controllerName = args[2];
        String outputJmxPath = args[3];
        */
        String inputJmxPath = "src/main/resources/testplan.jmx";
        String burpDumpPath = "src/main/resources/peticiones_prueba.xml";
        String controllerName = "TEST";
        String outputJmxPath = "src/main/resources/output.jmx";

        try {
            // Parse JMX template
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document jmxDoc = dBuilder.parse(new File(inputJmxPath));

            // Parse Burp dump
            Document burpDoc = dBuilder.parse(new File(burpDumpPath));

            // Find the ThreadGroup's hashTree where we'll add our controller
            NodeList hashTrees = jmxDoc.getElementsByTagName("hashTree");
            Element threadGroupHashTree = findThreadGroupHashTree(hashTrees);

            if (threadGroupHashTree == null) {
                System.err.println("Could not find ThreadGroup hashTree in JMX template");
                return;
            }

            // Create new GenericController
            Element genericController = createGenericController(jmxDoc, controllerName);
            Element controllerHashTree = jmxDoc.createElement("hashTree");

            // Process all requests from Burp dump
            NodeList items = burpDoc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                Element httpSampler = createHttpSamplerFromBurpItem(jmxDoc, item);
                if (httpSampler != null) {
                    controllerHashTree.appendChild(httpSampler);
                    controllerHashTree.appendChild(jmxDoc.createElement("hashTree"));
                }
            }

            // Add controller to the test plan
            threadGroupHashTree.appendChild(genericController);
            threadGroupHashTree.appendChild(controllerHashTree);

            // Save modified JMX
            saveJmxDocument(jmxDoc, outputJmxPath);

            System.out.println("Successfully created new JMX file with " + items.getLength() +
                    " requests in controller '" + controllerName + "'");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Element findThreadGroupHashTree(NodeList hashTrees) {
        for (int i = 0; i < hashTrees.getLength(); i++) {
            Element hashTree = (Element) hashTrees.item(i);
            Node previousSibling = hashTree.getPreviousSibling();
            while (previousSibling != null && previousSibling.getNodeType() != Node.ELEMENT_NODE) {
                previousSibling = previousSibling.getPreviousSibling();
            }
            if (previousSibling != null &&
                    "ThreadGroup".equals(previousSibling.getAttributes().getNamedItem("testclass").getNodeValue())) {
                return hashTree;
            }
        }
        return null;
    }

    private static Element createGenericController(Document doc, String name) {
        Element controller = doc.createElement("GenericController");
        controller.setAttribute("guiclass", "LogicControllerGui");
        controller.setAttribute("testclass", "GenericController");
        controller.setAttribute("testname", name);
        return controller;
    }

    private static Element createHttpSamplerFromBurpItem(Document doc, Element item) {
        try {
            String method = getElementText(item, "method");
            String url = getElementText(item, "url");
            String requestBase64 = getElementText(item, "request");
            String rawRequest = new String(Base64.getDecoder().decode(requestBase64));

            // Parse URL components
            Pattern urlPattern = Pattern.compile("(https?)://([^/]+)(/.*)?");
            Matcher matcher = urlPattern.matcher(url);
            if (!matcher.find()) {
                System.err.println("Invalid URL format: " + url);
                return null;
            }

            String protocol = matcher.group(1);
            String domain = matcher.group(2);
            String path = matcher.group(3) != null ? matcher.group(3) : "/";

            // Extract headers and body from request
            String[] requestParts = rawRequest.split("\r\n\r\n", 2);
            String headers = requestParts[0];
            String body = requestParts.length > 1 ? requestParts[1] : "";

            // Create HTTPSamplerProxy
            Element httpSampler = doc.createElement("HTTPSamplerProxy");
            httpSampler.setAttribute("guiclass", "HttpTestSampleGui");
            httpSampler.setAttribute("testclass", "HTTPSamplerProxy");
            httpSampler.setAttribute("testname", method + " " + path);

            // Set basic properties
            setStringProp(doc, httpSampler, "HTTPSampler.domain", domain);
            setStringProp(doc, httpSampler, "HTTPSampler.protocol", protocol);
            setStringProp(doc, httpSampler, "HTTPSampler.path", path);
            setStringProp(doc, httpSampler, "HTTPSampler.method", method);
            setBooleanProp(doc, httpSampler, "HTTPSampler.follow_redirects", true);
            setBooleanProp(doc, httpSampler, "HTTPSampler.use_keepalive", true);

            // Set request body if present
            if (!body.isEmpty() && ("POST".equals(method) || "PUT".equals(method))) {
                setBooleanProp(doc, httpSampler, "HTTPSampler.postBodyRaw", true);

                Element arguments = doc.createElement("elementProp");
                arguments.setAttribute("name", "HTTPsampler.Arguments");
                arguments.setAttribute("elementType", "Arguments");

                Element collectionProp = doc.createElement("collectionProp");
                collectionProp.setAttribute("name", "Arguments.arguments");

                Element elementProp = doc.createElement("elementProp");
                elementProp.setAttribute("name", "");
                elementProp.setAttribute("elementType", "HTTPArgument");

                setBooleanProp(doc, elementProp, "HTTPArgument.always_encode", false);
                setStringProp(doc, elementProp, "Argument.value", body);
                setStringProp(doc, elementProp, "Argument.metadata", "=");

                collectionProp.appendChild(elementProp);
                arguments.appendChild(collectionProp);
                httpSampler.appendChild(arguments);
            }

            return httpSampler;

        } catch (Exception e) {
            System.err.println("Error processing Burp item: " + e.getMessage());
            return null;
        }
    }

    private static String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return "";
    }

    private static void setStringProp(Document doc, Element parent, String name, String value) {
        Element prop = doc.createElement("stringProp");
        prop.setAttribute("name", name);
        prop.setTextContent(value);
        parent.appendChild(prop);
    }

    private static void setBooleanProp(Document doc, Element parent, String name, boolean value) {
        Element prop = doc.createElement("boolProp");
        prop.setAttribute("name", name);
        prop.setTextContent(value ? "true" : "false");
        parent.appendChild(prop);
    }

    private static void saveJmxDocument(Document doc, String outputPath) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(outputPath));
        transformer.transform(source, result);
    }
}