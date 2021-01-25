/*
 * Copyright 1999-2009 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.xml.security.keys.keyresolver.implementations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.xml.security.c14n.CanonicalizationException;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.keys.content.RetrievalMethod;
import org.apache.xml.security.keys.content.x509.XMLX509Certificate;
import org.apache.xml.security.keys.keyresolver.KeyResolver;
import org.apache.xml.security.keys.keyresolver.KeyResolverException;
import org.apache.xml.security.keys.keyresolver.KeyResolverSpi;
import org.apache.xml.security.keys.storage.StorageResolver;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.XMLUtils;
import org.apache.xml.security.utils.resolver.ResourceResolver;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * The RetrievalMethodResolver can retrieve public keys and certificates from
 * other locations. The location is specified using the ds:RetrievalMethod
 * element which points to the location. This includes the handling of raw
 * (binary) X.509 certificate which are not encapsulated in an XML structure.
 * If the retrieval process encounters an element which the
 * RetrievalMethodResolver cannot handle itself, resolving of the extracted
 * element is delegated back to the KeyResolver mechanism.
 *
 * @author $Author: raul $ modified by Dave Garcia
 */
public class RetrievalMethodResolver extends KeyResolverSpi {

    /** {@link org.apache.commons.logging} logging facility */
    static org.apache.commons.logging.Log log = 
        org.apache.commons.logging.LogFactory.getLog(
                        RetrievalMethodResolver.class.getName());

    /**
     * Method engineResolvePublicKey
     * @inheritDoc
     * @param element
     * @param BaseURI
     * @param storage
     */
    public PublicKey engineLookupAndResolvePublicKey(
           Element element, String BaseURI, StorageResolver storage)
    {
        if (!XMLUtils.elementIsInSignatureSpace(element,
               Constants._TAG_RETRIEVALMETHOD)) {      
	    return null;
        }

        try {
	    // Create a retrieval method over the given element
	    RetrievalMethod rm = new RetrievalMethod(element, BaseURI);
	    String type = rm.getType();		   
	    XMLSignatureInput resource = resolveInput(rm,BaseURI);
            if (RetrievalMethod.TYPE_RAWX509.equals(type)) {
                // a raw certificate, direct parsing is done!
            	X509Certificate cert = getRawCertificate(resource);
		if (cert != null) {
	            return cert.getPublicKey();
		}
		return null;
             }
	     Element e = obtainReferenceElement(resource); 
	     return resolveKey(e, BaseURI, storage);
         } catch (XMLSecurityException ex) {
             log.debug("XMLSecurityException", ex);
         } catch (CertificateException ex) {
             log.debug("CertificateException", ex);
         } catch (IOException ex) {
             log.debug("IOException", ex);
         } catch (ParserConfigurationException e) {
	     log.debug("ParserConfigurationException", e);
         } catch (SAXException e) {
	     log.debug("SAXException", e);
         } 
         return null;
    }

    static private Element obtainReferenceElement(XMLSignatureInput resource) 
        throws CanonicalizationException, ParserConfigurationException, 
        IOException, SAXException, KeyResolverException {
        Element e;
        if (resource.isElement()){
            e = (Element) resource.getSubNode();
        } else if (resource.isNodeSet()) {
	    // Retrieved resource is a nodeSet
	    e = getDocumentElement(resource.getNodeSet());
        } else {
	    // Retrieved resource is an inputStream
	    byte inputBytes[] = resource.getBytes();
	    e = getDocFromBytes(inputBytes);
	    // otherwise, we parse the resource, create an Element and delegate
	    if (log.isDebugEnabled()) 
	        log.debug("we have to parse " + inputBytes.length + " bytes");
        }
        return e;
    }

    /**
     * Method engineResolveX509Certificate
     * @inheritDoc
     * @param element
     * @param BaseURI
     * @param storage
     */
    public X509Certificate engineLookupResolveX509Certificate(
        Element element, String BaseURI, StorageResolver storage) {
        if (!XMLUtils.elementIsInSignatureSpace(element,
               Constants._TAG_RETRIEVALMETHOD)) {      
	     return null;
        }

        try {
            RetrievalMethod rm = new RetrievalMethod(element, BaseURI);
	    String type = rm.getType();		   
	    XMLSignatureInput resource = resolveInput(rm, BaseURI);
	    if (RetrievalMethod.TYPE_RAWX509.equals(type)) {
        	X509Certificate cert = getRawCertificate(resource);
                return cert;
	    } 
	    Element e = obtainReferenceElement(resource);
	    return resolveCertificate(e, BaseURI, storage);
        } catch (XMLSecurityException ex) {
            log.debug("XMLSecurityException", ex);
        } catch (CertificateException ex) {
            log.debug("CertificateException", ex);
        } catch (IOException ex) {
            log.debug("IOException", ex);
        } catch (ParserConfigurationException e) {
	    log.debug("ParserConfigurationException", e);
        } catch (SAXException e) {
	    log.debug("SAXException", e);
        } 
        return null;
    }
   
    /**
     * Retrieves a x509Certificate from the given information
     * @param e
     * @param BaseURI
     * @param storage
     * @return
     * @throws KeyResolverException 
     */
    static private X509Certificate resolveCertificate(Element e,
        String BaseURI, StorageResolver storage) throws KeyResolverException {
        if (log.isDebugEnabled()) {
	    log.debug("Now we have a {" + e.getNamespaceURI() + "}"
                + e.getLocalName() + " Element");
        }
        // An element has been provided
        if (e != null) { 
	    return KeyResolver.getX509Certificate(e, BaseURI, storage);
        }
	return null;
    } 
   
    /**
     * Retrieves a x509Certificate from the given information
     * @param e
     * @param BaseURI
     * @param storage
     * @return
     * @throws KeyResolverException 
     */
    static private PublicKey resolveKey(Element e, String BaseURI, 
        StorageResolver storage) throws KeyResolverException {
        if (log.isDebugEnabled()) {
	    log.debug("Now we have a {" + e.getNamespaceURI() + "}"
                + e.getLocalName() + " Element");
        }
        // An element has been provided
       	if (e != null) { 
	    return KeyResolver.getPublicKey(e, BaseURI, storage);
        }
        return null;
    }

    static private X509Certificate getRawCertificate(XMLSignatureInput resource)
    throws CanonicalizationException, IOException, CertificateException {
        byte inputBytes[] = resource.getBytes();	   
        // if the resource stores a raw certificate, we have to handle it
        CertificateFactory certFact = 
            CertificateFactory.getInstance(XMLX509Certificate.JCA_CERT_ID);
        X509Certificate cert = (X509Certificate) 
            certFact.generateCertificate(new ByteArrayInputStream(inputBytes));
        return cert;
    }

    /**
     * Resolves the input from the given retrieval method 
     * @return
     * @throws XMLSecurityException 
     */
    static private XMLSignatureInput resolveInput(RetrievalMethod rm,
        String BaseURI) throws XMLSecurityException {
        Attr uri = rm.getURIAttr();
        // Apply the transforms
        Transforms transforms = rm.getTransforms();
        ResourceResolver resRes = ResourceResolver.getInstance(uri, BaseURI);
        if (resRes != null) {
            XMLSignatureInput resource = resRes.resolve(uri, BaseURI);
            if (transforms != null) {
                log.debug("We have Transforms");
	        resource = transforms.performTransforms(resource);
            }		  
	    return resource;
        }
        return null;
    }
   
    /**
     * Parses a byte array and returns the parsed Element.
     *
     * @param bytes
     * @return the Document Element after parsing bytes 
     * @throws KeyResolverException if something goes wrong
     */
    static Element getDocFromBytes(byte[] bytes) throws KeyResolverException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(bytes));
            return doc.getDocumentElement();
        } catch (SAXException ex) {
            throw new KeyResolverException("empty", ex);
        } catch (IOException ex) {
            throw new KeyResolverException("empty", ex);
        } catch (ParserConfigurationException ex) {
            throw new KeyResolverException("empty", ex);
        }
    }

    /**
     * Method engineResolveSecretKey
     * @inheritDoc
     * @param element
     * @param BaseURI
     * @param storage
     */
    public javax.crypto.SecretKey engineLookupAndResolveSecretKey(
           Element element, String BaseURI, StorageResolver storage)
    {
        return null;
    }
   
    static Element getDocumentElement(Set set) {
        Iterator it = set.iterator();
        Element e = null;
        while (it.hasNext()) {
	    Node currentNode = (Node) it.next();
	    if (currentNode != null && Node.ELEMENT_NODE == currentNode.getNodeType()) {
	        e = (Element) currentNode;
	        break;
	    }
        }
        List parents = new ArrayList(10);
  		
  	// Obtain all the parents of the elemnt
  	while (e != null) {
  	    parents.add(e);
  	    Node n = e.getParentNode();
  	    if (n == null || Node.ELEMENT_NODE != n.getNodeType()) {
  		break;
  	    }
  	    e = (Element) n;
  	}
  	// Visit them in reverse order.
  	ListIterator it2 = parents.listIterator(parents.size()-1);
  	Element ele = null;
  	while (it2.hasPrevious()) {
  	    ele = (Element) it2.previous();
  	    if (set.contains(ele)) {
  		return ele;
  	    }
     	}
	return null;
    }
}
