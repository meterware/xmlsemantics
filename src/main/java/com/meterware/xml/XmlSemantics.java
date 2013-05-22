package com.meterware.xml;
/********************************************************************************************************************
 *
 * Copyright (c) 2003-2013, Russell Gold
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions
 * of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 *******************************************************************************************************************/

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * @author <a href="mailto:russgold@gmail.com">Russell Gold</a>
 */
public class XmlSemantics {

    private final static String LINE_BREAK = System.getProperty("line.separator");
    private static final Class[] NO_ARG_CLASSES = new Class[0];
    private static final Object[] NO_ARGS = new Object[0];
    private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("d-MMM-yyyy");

    /**
     * Given an XML DOM and a new object, fills in the object with values from the DOM and returns it.
     * @param document the DOM achieved through parsing an XML file
     * @param documentRoot the object which should be populated from the XML DOM
     * @param documentName the name of the document, used for error message generation.
     * @param <T> the type of the object to populate
     * @return the populated object
     */
    public static <T> T build(Document document, T documentRoot, String documentName) {
        try {
            interpretNode(getRootNode(document), documentRoot);
            return documentRoot;
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Error interpreting document: " + documentName + ": " + e.getTargetException());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error interpreting document: " + documentName + ": " + e);
        }
    }


    private static Node getRootNode(Document document) {
        Node node = document.getFirstChild();
        while (node != null && node.getNodeType() != Node.ELEMENT_NODE) node = node.getNextSibling();
        if (node != null) return node;
        throw new RuntimeException("Document has no root node");
    }


    private static void interpretNode(final Node node, Object elementObject) throws IntrospectionException, IllegalAccessException, InvocationTargetException {
        Class elementClass = elementObject.getClass();
        BeanInfo beanInfo = Introspector.getBeanInfo(elementClass, Object.class);
        NamedNodeMap nnm = node.getAttributes();
        if (nnm != null) {
            for (int i = 0; i < nnm.getLength(); i++) {
                Node attribute = nnm.item(i);
                setProperty(elementObject, beanInfo, toPropertyName(attribute.getNodeName()), attribute.getNodeValue());
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                interpretNestedElement(elementClass, elementObject, beanInfo, child);
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                setContentsAsProperty(elementObject, beanInfo, node, "text");
            }
        }
    }


    private static String toPropertyName(String attributeName) {
        if (attributeName.indexOf('-') < 0) return attributeName;
        int index;
        while ((index = attributeName.indexOf('-')) > 0) {
            attributeName = attributeName.substring(0, index)
                    + Character.toUpperCase(attributeName.charAt(index + 1))
                    + attributeName.substring(index + 2);
        }
        return attributeName;
    }


    private static void interpretNestedElement(Class elementClass, Object elementObject, BeanInfo beanInfo, Node child) throws IllegalAccessException, InvocationTargetException, IntrospectionException {
        try {
            Method method = elementClass.getMethod(getCreateMethodName(child.getNodeName()), NO_ARG_CLASSES);
            Object subElement = method.invoke(elementObject, NO_ARGS);
            interpretNode(child, subElement);
        } catch (NoSuchMethodException e) {
            setContentsAsProperty(elementObject, beanInfo, child, child.getNodeName());
        } catch (SecurityException e) {
            e.printStackTrace();  //To change body of catch statement use Options | File Templates.
        }
    }


    private static String getCreateMethodName(String nodeName) {
        nodeName = toPropertyName(nodeName);
        StringBuilder sb = new StringBuilder("create");
        sb.append(Character.toUpperCase(nodeName.charAt(0)));
        sb.append(nodeName.substring(1));
        return sb.toString();
    }


    private static void setContentsAsProperty(Object elementObject, BeanInfo beanInfo, Node propertyNode, String propertyName) throws IllegalAccessException, InvocationTargetException {
        StringBuilder sb = new StringBuilder();
        boolean haveAllContents = false;
        for (Node child = propertyNode.getFirstChild(); child != null && !haveAllContents; child = child.getNextSibling()) {
            switch (child.getNodeType()) {
                case Node.TEXT_NODE:
                    sb.append(child.getNodeValue().trim());
                    break;
                case Node.CDATA_SECTION_NODE:
                    sb.append(child.getNodeValue());
                    break;
                default:
                    haveAllContents = true;
            }
        }
        setProperty(elementObject, beanInfo, propertyName, sb.toString());
    }


    private static void setProperty(Object elementObject, BeanInfo beanInfo, final String propertyName, final String propertyValue) throws IllegalAccessException, InvocationTargetException {
        PropertyDescriptor descriptor = getProperty(beanInfo, toPropertyName(propertyName));
        if (descriptor == null || descriptor.getWriteMethod() == null) return;
        Class propertyType = descriptor.getPropertyType();
        Method writeMethod = descriptor.getWriteMethod();
        Object[] args = toPropertyArgumentArray(propertyType, propertyValue);
        writeMethod.invoke(elementObject, args);
    }


    private static Object[] toPropertyArgumentArray(Class propertyType, String nodeValue) {
        if (propertyType.equals(String.class)) {
            return new Object[]{nodeValue};
        } else if (propertyType.equals(int.class)) {
            return new Object[]{new Integer(nodeValue)};
        } else if (propertyType.equals(Date.class)) {
            return new Object[]{new Date(nodeValue)};
        } else if (propertyType.equals(boolean.class)) {
            return new Object[]{Boolean.valueOf(nodeValue)};
        } else {
            throw new RuntimeException(propertyType + " attributes not supported");
        }
    }


    private static PropertyDescriptor getProperty(BeanInfo beanInfo, String propertyName) {
        PropertyDescriptor properties[] = beanInfo.getPropertyDescriptors();
        for (PropertyDescriptor property : properties) {
            if (property.getName().equals(propertyName)) return property;
        }
        return null;
    }


    /**
     * Reads a file from the file system and parses it as a DOM.
     * @param file the XML file to read
     * @return the DOM created from the XML file
     * @throws SAXException if an error is detected while parsing the file
     * @throws IOException if an error is detected while reading the file
     */
    public static Document parseDocument(File file) throws SAXException, IOException {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Unable to create a parser for XML documents: " + e);
        }
    }


    /**
     * Reads an XML document from a string and parses it as a DOM.
     * @param xml the string to read
     * @return the DOM created from the XML string
     * @throws SAXException if an error is detected while parsing the string
     * @throws IOException if an error is detected while reading the string - should never happen
     */
    public static Document parseDocument(String xml) throws SAXException, IOException {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Unable to create a parser for XML documents: " + e);
        }
    }


    /**
     * Generates XML as a string from the specified root element.
     * @param node the root element from a DOM
     * @return the created XML
     */
    public static String generateXML(WriteableRootElement node) {
        StringBuffer sb = new StringBuffer("<?xml version='1.0' ?>").append(LINE_BREAK);
        appendElement(sb, node, "");
        return sb.toString();
    }


    private static void appendElement(StringBuffer sb, WriteableXMLElement element, String prefix) {
        sb.append(prefix).append('<').append(element.getElementName());
        String[] attributeNames = element.getAttributeNames();
        for (String attributeName : attributeNames) {
            if (element.isExplicitAttribute(attributeName)) {
                sb.append(' ').append(attributeName).append("='").append(getStringProperty(attributeName, element)).append("'");
            }
        }
        boolean isEmpty = true;
        final String contents = element.getContents();
        if (contents != null) {
            sb.append('>');
            sb.append(contents);
            sb.append("</").append(element.getElementName()).append('>').append(LINE_BREAK);
        } else {
            String[] nestedElementNames = element.getNestedElementNames();
            for (String nestedElementName : nestedElementNames) {
                WriteableXMLElement[] children = element.getNestedElements(nestedElementName);
                for (WriteableXMLElement child : children) {
                    if (isEmpty) {
                        isEmpty = false;
                        sb.append('>').append(LINE_BREAK);
                    }
                    appendElement(sb, child, prefix + "   ");
                }
            }
            if (isEmpty) {
                sb.append("/>").append(LINE_BREAK);
            } else {
                sb.append(prefix).append("</").append(element.getElementName()).append('>').append(LINE_BREAK);
            }
        }
    }


    private static String getStringProperty(String propertyName, Object template) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(template.getClass(), Object.class);
            final PropertyDescriptor propertyDescriptor = getProperty(beanInfo, propertyName);
            if (propertyDescriptor == null) return "";
            Object result = propertyDescriptor.getReadMethod().invoke(template, NO_ARGS);
            if (result instanceof Date) result = DATE_FORMAT.format((Date) result);
            return result == null ? "" : result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to get '" + propertyName + "' property: " + e);
        }
    }

}
