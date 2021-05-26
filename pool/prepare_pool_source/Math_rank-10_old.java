package org.jsoup.parser;

import org.apache.commons.lang.Validate;
import org.jsoup.nodes.*;

import java.util.*;

/**
 Parses HTML into a {@link Document}

 @author Jonathan Hedley, jonathan@hedley.net */
public class Parser {
    private static String SQ = "'";
    private static String DQ = "\"";

    private static Tag htmlTag = Tag.valueOf("html");
    private static Tag headTag = Tag.valueOf("head");
    private static Tag bodyTag = Tag.valueOf("body");
    private static Tag titleTag = Tag.valueOf("title");

    private LinkedList<Element> stack;
    private TokenQueue tq;
    private Document doc;

    public Parser(String html) {
        Validate.notNull(html);

        stack = new LinkedList<Element>();
        tq = new TokenQueue(html);

        doc = new Document();
        stack.add(doc);
    }

    public static Document parse(String html) {
        Parser parser = new Parser(html);
        return parser.parse();
    }

    public Document parse() {
        while (!tq.isEmpty()) {
            if (tq.matches("<!--")) {
                parseComment();
            } else if (tq.matches("<?") || tq.matches("<!")) {
                parseXmlDecl();
            } else if (tq.matches("</")) {
                parseEndTag();
            } else if (tq.matches("<")) {
                parseStartTag();
            } else {
                parseTextNode();
            }
        }
        return doc;
    }

    private void parseComment() {
        // TODO: this puts comments into nodes that should not hold them (e.g. img).
        tq.consume("<!--");
        String data = tq.chompTo("->");

        if (data.endsWith("-")) // i.e. was -->
            data = data.substring(0, data.length()-1);
        Comment comment = new Comment(data);
        last().addChild(comment);
    }

    private void parseXmlDecl() {
        tq.consume("<"); tq.consume(); // <? or <!, from initial match.
        String data = tq.chompTo(">");

        XmlDeclaration decl = new XmlDeclaration(data);
        last().addChild(decl);
    }

    private void parseEndTag() {
        tq.consume("</");
        String tagName = tq.consumeWord();
        tq.chompTo(">");

        if (!tagName.isEmpty()) {
            Tag tag = Tag.valueOf(tagName);
            popStackToClose(tag);
        }
    }

    private void parseStartTag() {
        tq.consume("<");
        Attributes attributes = new Attributes();

        String tagName = tq.consumeWord();
        while (!tq.matchesAny("<", "/>", ">") && !tq.isEmpty()) {
            Attribute attribute = parseAttribute();
            if (attribute != null)
                attributes.put(attribute);
        }

        Tag tag = Tag.valueOf(tagName);
        StartTag startTag = new StartTag(tag, attributes);
        Element child = new Element(startTag);

        if (!tq.matchChomp("/>")) { // close empty element or tag
            tq.matchChomp(">");
        }

        // pc data only tags (textarea, script): chomp to end tag, add content as text node
        if (tag.isData()) {
            String data = tq.chompTo("</" + tagName);
            tq.chompTo(">");
            TextNode textNode = TextNode.createFromEncoded(data); // TODO: maybe have this be another data type? So doesn't come back in text()?
            child.addChild(textNode);

            if (tag.equals(titleTag))
                doc.setTitle(child.text());
        }
        addChildToParent(child);
    }

    private Attribute parseAttribute() {
        tq.consumeWhitespace();
        String key = tq.consumeWord();
        String value = "";
        tq.consumeWhitespace();
        if (tq.matchChomp("=")) {
            tq.consumeWhitespace();

            if (tq.matchChomp(SQ)) {
                value = tq.chompTo(SQ);
            } else if (tq.matchChomp(DQ)) {
                value = tq.chompTo(DQ);
            } else {
                StringBuilder valueAccum = new StringBuilder();
                // no ' or " to look for, so scan to end tag or space (or end of stream)
                while (!tq.matchesAny("<", "/>", ">") && !tq.matchesWhitespace() && !tq.isEmpty()) {
                    valueAccum.append(tq.consume());
                }
                value = valueAccum.toString();
            }
            tq.consumeWhitespace();
        }
        if (!key.isEmpty())
            return Attribute.createFromEncoded(key, value);
        else {
            tq.consume(); // unknown char, keep popping so not get stuck
            return null;
        }
    }

    private void parseTextNode() {
        // TODO: work out whitespace requirements (between blocks, between inlines)
        String text = tq.consumeTo("<");
        TextNode textNode = TextNode.createFromEncoded(text);
        last().addChild(textNode);
    }

    private Element addChildToParent(Element child) {
        Element parent = popStackToSuitableContainer(child.getTag());
        Tag childTag = child.getTag();
        boolean validAncestor = stackHasValidParent(childTag);

        if (!validAncestor) {
            // create implicit parent around this child
            Tag parentTag = childTag.getImplicitParent();
            StartTag parentStart = new StartTag(parentTag);
            Element implicit = new Element(parentStart);
            // special case: make sure there's a head before putting in body
            if (child.getTag().equals(bodyTag)) {
                Element head = new Element(new StartTag(headTag));
                implicit.addChild(head);
            }
            implicit.addChild(child);

            // recurse to ensure somewhere to put parent
            Element root = addChildToParent(implicit);
            stack.addLast(child);
            return root;
        }

        parent.addChild(child);
        stack.addLast(child);
        return parent;
    }

    private boolean stackHasValidParent(Tag childTag) {
        if (stack.size() == 1 && childTag.equals(htmlTag))
            return true; // root is valid for html node
        
        for (int i = stack.size() -1; i > 0; i--) { // not all the way to end
            Element el = stack.get(i);
            Tag parent2 = el.getTag();
            if (parent2.isValidParent(childTag)) {
                return true;
            }
        }
        return false;
    }

    private Element popStackToSuitableContainer(Tag tag) {
        while (!stack.isEmpty()) {
            if (last().getTag().canContain(tag))
                return last();
            else
                stack.removeLast();
        }
        return null;
    }

    private Element popStackToClose(Tag tag) {
        // first check to see if stack contains this tag; if so pop to there, otherwise ignore
        int counter = 0;
        Element elToClose = null;
        for (int i = stack.size() -1; i > 0; i--) {
            counter++;
            Element el = stack.get(i);
            if (el.getTag().equals(tag)) {
                elToClose = el;
                break;
            }
        }
        if (elToClose != null) {
            for (int i = 0; i < counter; i++) {
                stack.removeLast();
            }
        }
        return elToClose;
    }

    private Element last() {
        return stack.getLast();
    }
}
