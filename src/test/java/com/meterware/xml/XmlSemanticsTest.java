package com.meterware.xml;

import org.junit.Test;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class XmlSemanticsTest {

    @Test
    public void populateFromAttributes() throws Exception {
        String xml = "<testdoc quantity1='value1' quantity2='2' when='May 17, 2012'/>";
        Document document = XmlSemantics.parseDocument(xml);
        TestObject testObject = XmlSemantics.build(document, new TestObject(), "fromAttributes");
        assertEquals("value1", testObject.quantity1);
        assertEquals(2, testObject.quantity2);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(testObject.when);
        assertEquals(2012, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.MAY, calendar.get(Calendar.MONTH));
        assertEquals(17, calendar.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void populateFromNestedElements() throws Exception {
        String xml = "<testdoc>" + "" +
                "       <quantity1>aValue</quantity1>" +
                "       <valueRecorded>true</valueRecorded>" +
                "     </testdoc>";
        Document document = XmlSemantics.parseDocument(xml);
        TestObject testObject = XmlSemantics.build(document, new TestObject(), "fromElements");
        assertEquals("aValue", testObject.quantity1);
        assertTrue(testObject.valueRecorded);
    }

    @Test
    public void recognizeHyphenatedName() throws Exception {
        String xml = "<testdoc>" + "" +
                "       <value-recorded>true</value-recorded>" +
                "     </testdoc>";
        Document document = XmlSemantics.parseDocument(xml);
        TestObject testObject = XmlSemantics.build(document, new TestObject(), "hyphenatedName");
        assertTrue(testObject.valueRecorded);
    }

    @Test
    public void populateFromCData() throws Exception {
        String xml = "<testdoc>" + "" +
                "       <quantity1><![CDATA[ a phrase]]></quantity1>" +
                "     </testdoc>";
        Document document = XmlSemantics.parseDocument(xml);
        TestObject testObject = XmlSemantics.build(document, new TestObject(), "cData");
        assertEquals(" a phrase", testObject.quantity1);
    }

    @Test
    public void populateNestedObject() throws Exception {
        String xml = "<testdoc>" + "" +
                "       <color name='red'/>" +
                "       <color name='green'/>" +
                "     </testdoc>";
        Document document = XmlSemantics.parseDocument(xml);
        TestObject testObject = XmlSemantics.build(document, new TestObject(), "nestedObjects");
        assertEquals("red", testObject.colors.get(0).name);
        assertEquals("green", testObject.colors.get(1).name);
    }

    static public class TestObject {
        private String quantity1;
        private int quantity2;
        private boolean valueRecorded;
        private Date when;
        private List<TestColor> colors = new ArrayList<TestColor>();

        public void setQuantity1(String quantity1) {
            this.quantity1 = quantity1;
        }

        public void setQuantity2(int quantity2) {
            this.quantity2 = quantity2;
        }

        public void setValueRecorded(boolean valueRecorded) {
            this.valueRecorded = valueRecorded;
        }

        public void setWhen(Date when) {
            this.when = when;
        }

        public TestColor createColor() {
            TestColor color = new TestColor();
            colors.add(color);
            return color;
        }
    }

    static public class TestColor {
        private String name;

        public void setName(String name) {
            this.name = name;
        }
    }

}
