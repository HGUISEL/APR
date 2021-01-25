package org.apache.maven.doxia.module.fml;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;

import org.apache.maven.doxia.util.HtmlTools;
import org.apache.maven.doxia.module.fml.model.Faq;
import org.apache.maven.doxia.module.fml.model.Faqs;
import org.apache.maven.doxia.module.fml.model.Part;
import org.apache.maven.doxia.parser.ParseException;
import org.apache.maven.doxia.parser.Parser;
import org.apache.maven.doxia.sink.Sink;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;

/**
 * Parse a fml model and emit events into the specified doxia Sink.
 *
 * @author <a href="mailto:evenisse@codehaus.org">Emmanuel Venisse</a>
 * @version $Id$
 * @since 1.0
 * @plexus.component role-hint="fml"
 */
public class FmlParser
    implements Parser
{
    /** {@inheritDoc} */
    public void parse( Reader reader, Sink sink )
        throws ParseException
    {
        Faqs faqs;
        try
        {
            XmlPullParser parser = new MXParser();

            parser.setInput( reader );

            faqs = parseFml( parser, sink );
        }
        catch ( Exception ex )
        {
            throw new ParseException( "Error parsing the model: " + ex.getMessage(), ex );
        }

        try
        {
            createSink( faqs, sink );
        }
        catch ( Exception e )
        {
            throw new ParseException( "Error creating sink: " + e.getMessage(), e );
        }
    }

    /** {@inheritDoc} */
    public int getType()
    {
        return XML_TYPE;
    }

    /**
     * @param parser
     * @param sink
     * @return Faqs
     * @throws Exception
     */
    public Faqs parseFml( XmlPullParser parser, Sink sink )
        throws Exception
    {
        Faqs faqs = new Faqs();

        Part currentPart = null;

        Faq currentFaq = null;

        boolean inFaq = false;

        boolean inPart = false;

        boolean inQuestion = false;

        boolean inAnswer = false;

        StringBuffer buffer = null;

        int eventType = parser.getEventType();

        while ( eventType != XmlPullParser.END_DOCUMENT )
        {
            if ( eventType == XmlPullParser.START_TAG )
            {
                if ( parser.getName().equals( "faqs" ) )
                {
                    String title = parser.getAttributeValue( null, "title" );

                    if ( title != null )
                    {
                        faqs.setTitle( title );
                    }

                    String toplink = parser.getAttributeValue( null, "toplink" );

                    if ( toplink != null )
                    {
                        if ( toplink.equalsIgnoreCase( "true" ) )
                        {
                            faqs.setToplink( true );
                        }
                        else
                        {
                            faqs.setToplink( false );
                        }
                    }
                }
                else if ( parser.getName().equals( "part" ) )
                {
                    inPart = true;
                    currentPart = new Part();
                    currentPart.setId( parser.getAttributeValue( null, "id" ) );
                }
                else if ( parser.getName().equals( "title" ) )
                {
                    currentPart.setTitle( parser.nextText().trim() );
                }
                else if ( parser.getName().equals( "faq" ) )
                {
                    inFaq = true;
                    currentFaq = new Faq();
                    currentFaq.setId( parser.getAttributeValue( null, "id" ) );
                }
                if ( parser.getName().equals( "question" ) )
                {
                    buffer = new StringBuffer();
                    inQuestion = true;
                }
                else if ( parser.getName().equals( "answer" ) )
                {
                    buffer = new StringBuffer();
                    inAnswer = true;
                }
                else if ( inQuestion || inAnswer )
                {
                    buffer.append( "<" );

                    buffer.append( parser.getName() );

                    int count = parser.getAttributeCount();

                    for ( int i = 0; i < count; i++ )
                    {
                        buffer.append( " " );

                        buffer.append( parser.getAttributeName( i ) );

                        buffer.append( "=" );

                        buffer.append( "\"" );

                        buffer.append( HtmlTools.escapeHTML( parser.getAttributeValue( i ) ) );

                        buffer.append( "\"" );
                    }

                    buffer.append( ">" );
                }
            }
            else if ( eventType == XmlPullParser.END_TAG )
            {
                if ( parser.getName().equals( "faqs" ) )
                {
                    // Do nothing
                }
                else if ( parser.getName().equals( "part" ) )
                {
                    faqs.addPart( currentPart );

                    currentPart = null;

                    inPart = false;
                }
                else if ( parser.getName().equals( "faq" ) )
                {
                    currentPart.addFaq( currentFaq );

                    currentFaq = null;

                    inFaq = false;
                }
                if ( parser.getName().equals( "question" ) )
                {
                    currentFaq.setQuestion( buffer.toString() );

                    inQuestion = false;
                }
                else if ( parser.getName().equals( "answer" ) )
                {
                    currentFaq.setAnswer( buffer.toString() );

                    inAnswer = false;
                }
                else if ( inQuestion || inAnswer )
                {
                    if ( buffer.charAt( buffer.length() - 1 ) == ' ' )
                    {
                        buffer.deleteCharAt( buffer.length() - 1 );
                    }

                    buffer.append( "</" );

                    buffer.append( parser.getName() );

                    buffer.append( ">" );
                }
            }
            else if ( eventType == XmlPullParser.CDSECT )
            {
                if ( buffer != null && parser.getText() != null )
                {
                    buffer.append( "<![CDATA[" );
                    buffer.append( parser.getText() );
                    buffer.append( "]]>" );
                }
            }
            else if ( eventType == XmlPullParser.TEXT )
            {
                if ( buffer != null && parser.getText() != null )
                {
                    buffer.append( parser.getText() );
                }
            }
            else if ( eventType == XmlPullParser.ENTITY_REF )
            {
                if ( buffer != null && parser.getText() != null )
                {
                    buffer.append( HtmlTools.escapeHTML( parser.getText() ) );
                }
            }

            eventType = parser.nextToken();
        }

        return faqs;
    }

    /**
     * @param faqs
     * @param sink
     * @throws Exception
     */
    private void createSink( Faqs faqs, Sink sink )
        throws Exception
    {
        sink.head();
        sink.title();
        sink.text( faqs.getTitle() );
        sink.title_();
        sink.head_();

        sink.body();
        sink.section1();
        sink.sectionTitle1();
        sink.anchor( "top" );
        sink.text( faqs.getTitle() );
        sink.anchor_();
        sink.sectionTitle1_();

        // ----------------------------------------------------------------------
        // Write summary
        // ----------------------------------------------------------------------

        for ( Iterator partIterator = faqs.getParts().iterator(); partIterator.hasNext(); )
        {
            Part part = (Part) partIterator.next();
            if ( StringUtils.isNotEmpty( part.getTitle() ) )
            {
                sink.paragraph();
                sink.bold();
                sink.text( part.getTitle() );
                sink.bold_();
                sink.paragraph_();
            }

            sink.numberedList( Sink.NUMBERING_DECIMAL );
            for ( Iterator faqIterator = part.getFaqs().iterator(); faqIterator.hasNext(); )
            {
                Faq faq = (Faq) faqIterator.next();
                sink.numberedListItem();
                sink.link( "#" + HtmlTools.encodeId( faq.getId() ) );
                sink.rawText( faq.getQuestion() );
                sink.link_();
                sink.numberedListItem_();
            }
            sink.numberedList_();
        }
        sink.section1_();

        // ----------------------------------------------------------------------
        // Write content
        // ----------------------------------------------------------------------

        for ( Iterator partIterator = faqs.getParts().iterator(); partIterator.hasNext(); )
        {
            Part part = (Part) partIterator.next();
            if ( StringUtils.isNotEmpty( part.getTitle() ) )
            {
                sink.section1();
                sink.sectionTitle1();
                sink.text( part.getTitle() );
                sink.sectionTitle1_();
            }

            sink.definitionList();
            for ( Iterator faqIterator = part.getFaqs().iterator(); faqIterator.hasNext(); )
            {
                Faq faq = (Faq) faqIterator.next();
                sink.definedTerm();
                sink.anchor( faq.getId() );
                sink.rawText( faq.getQuestion() );
                sink.anchor_();
                sink.definedTerm_();
                sink.definition();
                sink.paragraph();
                writeAnswer( sink, faq.getAnswer() );
                sink.paragraph_();

                if ( faqs.isToplink() )
                {
                    writeTopLink( sink );
                }

                if ( faqIterator.hasNext() )
                {
                    sink.horizontalRule();
                }
                sink.definition_();
            }
            sink.definitionList_();

            if ( StringUtils.isNotEmpty( part.getTitle() ) )
            {
                sink.section1_();
            }
        }

        sink.body_();
    }

    /**
     * @param sink
     * @param answer
     * @throws Exception
     */
    private void writeAnswer( Sink sink, String answer )
        throws Exception
    {
        int startSource = answer.indexOf( "<source>" );
        if ( startSource != -1 )
        {
            writeAnswerWithSource( sink, answer );
        }
        else
        {
            sink.rawText( answer );
        }
    }

    /**
     * @param sink
     */
    private void writeTopLink( Sink sink )
    {
        sink.rawText( "<table border=\"0\">" );
        sink.rawText( "<tr><td align=\"right\">" );

        sink.link( "#top" );
        sink.text( "[top]" );
        sink.link_();

        sink.rawText( "</td></tr>" );
        sink.rawText( "</table>" );
    }

    /**
     * @param sink
     * @param answer
     * @throws Exception
     */
    private void writeAnswerWithSource( Sink sink, String answer )
        throws Exception
    {
        XmlPullParser parser = new MXParser();
        parser.setInput( new StringReader( "<answer>" + answer + "</answer>" ) );

        int countSource = 0;
        int eventType = parser.getEventType();

        while ( eventType != XmlPullParser.END_DOCUMENT )
        {
            if ( eventType == XmlPullParser.START_TAG )
            {
                if ( parser.getName().equals( "source" ) && countSource == 0 )
                {
                    sink.verbatim( true );
                    countSource++;
                }
                else if ( parser.getName().equals( "source" ) )
                {
                    sink.rawText( HtmlTools.escapeHTML( "<" + parser.getName() + ">" ) );
                    countSource++;
                }
                else if ( parser.getName().equals( "answer" ) )
                {
                    // nop
                }
                else
                {
                    if ( countSource > 0 )
                    {
                        sink.rawText( HtmlTools.escapeHTML( "<" + parser.getName() + ">" ) );
                    }
                    else
                    {
                        StringBuffer buffer = new StringBuffer();
                        buffer.append( "<" + parser.getName() );

                        int count = parser.getAttributeCount();

                        for ( int i = 0; i < count; i++ )
                        {
                            buffer.append( " " );

                            buffer.append( parser.getAttributeName( i ) );

                            buffer.append( "=" );

                            buffer.append( "\"" );

                            buffer.append( HtmlTools.escapeHTML( parser.getAttributeValue( i ) ) );

                            buffer.append( "\"" );
                        }

                        buffer.append( ">" );

                        sink.rawText( buffer.toString() );
                    }
                }
            }
            else if ( eventType == XmlPullParser.END_TAG )
            {
                if ( parser.getName().equals( "source" ) && countSource == 1 )
                {
                    countSource--;
                    sink.verbatim_();
                }
                else if ( parser.getName().equals( "source" ) )
                {
                    sink.rawText( HtmlTools.escapeHTML( "</" + parser.getName() + ">" ) );
                    countSource--;
                }
                else if ( parser.getName().equals( "answer" ) )
                {
                    // nop
                }
                else
                {
                    if ( countSource > 0 )
                    {
                        sink.rawText( HtmlTools.escapeHTML( "</" + parser.getName() + ">" ) );
                    }
                    else
                    {
                        sink.rawText( "</" + parser.getName() + ">" );
                    }
                }
            }
            else if ( eventType == XmlPullParser.CDSECT )
            {
                sink.rawText( HtmlTools.escapeHTML( parser.getText() ) );
            }
            else if ( eventType == XmlPullParser.TEXT )
            {
                sink.rawText( HtmlTools.escapeHTML( parser.getText() ) );
            }
            else if ( eventType == XmlPullParser.ENTITY_REF )
            {
                sink.rawText( HtmlTools.escapeHTML( parser.getText() ) );
            }

            eventType = parser.nextToken();
        }
    }
}
