/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.axis2.handlers.addressing;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.AddressingConstants;
import org.apache.axis2.addressing.AnyContentType;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.addressing.MessageInformationHeaders;
import org.apache.axis2.addressing.ServiceName;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.handlers.AbstractHandler;
import org.apache.axis2.om.OMAbstractFactory;
import org.apache.axis2.om.OMElement;
import org.apache.axis2.om.OMNamespace;
import org.apache.axis2.soap.SOAPHeader;
import org.apache.axis2.soap.SOAPHeaderBlock;
import org.apache.wsdl.WSDLConstants;

import javax.xml.namespace.QName;
import java.util.Iterator;

public class AddressingOutHandler
        extends AbstractHandler
        implements AddressingConstants {

    private boolean isAddressingEnabled = true;

    // IN message, if any, has messageId and replyTo and faultTo addresses that needs to be used
    // in the OUT message. User may sometimes override these values, at his discretion .The following
    // boolean variable will create room for that.
    private boolean overrideINMessageInformation = false;

    OMNamespace addressingNamespaceObject;
    String addressingNamespace;


    public void invoke(MessageContext msgContext) throws AxisFault {


        if (!isAddressingEnabled || msgContext.getMessageInformationHeaders() == null) {
            return;
        }

        // first check whether current message context can be used to determin the addressing version to be used
        Object addressingVersionFromCurrentMsgCtxt = msgContext.getProperty(WS_ADDRESSING_VERSION);
        if (addressingVersionFromCurrentMsgCtxt != null) {
            // since we support only two addressing versions I can avoid multiple  ifs here.
            // see that if message context property holds something other than Final.WSA_NAMESPACE
            // we always defaults to Submission.WSA_NAMESPACE. Hope this is fine.
            addressingNamespace = Final.WSA_NAMESPACE.equals(addressingVersionFromCurrentMsgCtxt)
                    ? Final.WSA_NAMESPACE : Submission.WSA_NAMESPACE;
        } else if (msgContext.getOperationContext() != null) { // check for a IN message context, else default to WSA Submission
            MessageContext inMessageContext = msgContext.getOperationContext()
                    .getMessageContext(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
            if (inMessageContext == null) {
                addressingNamespace = Submission.WSA_NAMESPACE; // setting Submission version as the default addressing namespace
            } else {
                addressingNamespace =
                        (String) inMessageContext.getProperty(
                                WS_ADDRESSING_VERSION);
                if (addressingNamespace == null) {
                    addressingNamespace = Submission.WSA_NAMESPACE; // Addressing version has not been set in the IN path
                }
            }
        }

        if (addressingNamespace == null || "".equals(addressingNamespace)) {
            addressingNamespace = Submission.WSA_NAMESPACE;
        }
        addressingNamespaceObject =
                OMAbstractFactory.getOMFactory().createOMNamespace(
                        addressingNamespace, WSA_DEFAULT_PREFIX);


        MessageInformationHeaders messageInformationHeaders =
                msgContext.getMessageInformationHeaders();
        SOAPHeader soapHeader = msgContext.getEnvelope().getHeader();

        // by this time, we definitely have some addressing information to be sent. This is because,
        // we have tested at the start of this whether messageInformationHeaders are null or not.
        // So rather than declaring addressing namespace in each and every addressing header, lets
        // define that in the Header itself.
        soapHeader.declareNamespace(addressingNamespaceObject);

        // processing WSA To
        EndpointReference epr = messageInformationHeaders.getTo();
        if (epr != null && !isAddressingHeaderAlreadyAvailable(WSA_TO, soapHeader)) {

            String address = epr.getAddress();
            if (!"".equals(address) && address != null) {
                SOAPHeaderBlock toHeaderBlock = soapHeader.addHeaderBlock(
                        WSA_TO, addressingNamespaceObject);
                toHeaderBlock.setText(address);
            }

            AnyContentType referenceParameters = epr.getReferenceParameters();
            if (referenceParameters != null) {
                processAnyContentType(referenceParameters, soapHeader);
            }

            addToHeader(epr, soapHeader);
        }

        // processing WSA Action
        String action = messageInformationHeaders.getAction();
        if (action != null && !isAddressingHeaderAlreadyAvailable(WSA_ACTION, soapHeader)) {
            processStringInfo(action, WSA_ACTION, soapHeader);
        }

        // processing WSA replyTo
        if (!isAddressingHeaderAlreadyAvailable(WSA_REPLY_TO, soapHeader)) {
            epr = messageInformationHeaders.getReplyTo();
            if (epr == null) {//optional
                // setting anonymous URI. Defaulting to Final.
                String anonymousURI = Final.WSA_ANONYMOUS_URL;
                if (Submission.WSA_NAMESPACE.equals(addressingNamespace)) {
                    anonymousURI = Submission.WSA_ANONYMOUS_URL;
                }
                epr = new EndpointReference(anonymousURI);
            }
            // add the service group id as a reference parameter
            String serviceGroupContextId = msgContext.getServiceGroupContextId();
            if (serviceGroupContextId != null && !"".equals(serviceGroupContextId)) {
                if (epr.getReferenceParameters() == null) {
                    epr.setReferenceParameters(new AnyContentType());
                }
                epr.getReferenceParameters().addReferenceValue(new QName(Constants.AXIS2_NAMESPACE_URI,
                        Constants.SERVICE_GROUP_ID, Constants.AXIS2_NAMESPACE_PREFIX), serviceGroupContextId);
            }
            addToSOAPHeader(epr, AddressingConstants.WSA_REPLY_TO, soapHeader);
        }

        epr = messageInformationHeaders.getFrom();
        if (epr != null) {//optional
            addToSOAPHeader(epr, AddressingConstants.WSA_FROM, soapHeader);
        }

        epr = messageInformationHeaders.getFaultTo();
        if (epr != null) {//optional
            addToSOAPHeader(epr, AddressingConstants.WSA_FAULT_TO, soapHeader);
        }

        String messageID = messageInformationHeaders.getMessageId();
        if (messageID != null && !isAddressingHeaderAlreadyAvailable(WSA_MESSAGE_ID, soapHeader)) {//optional
            processStringInfo(messageID, WSA_MESSAGE_ID, soapHeader);
        }

        if (!isAddressingHeaderAlreadyAvailable(WSA_RELATES_TO, soapHeader)) {
            org.apache.axis2.addressing.RelatesTo relatesTo = messageInformationHeaders.getRelatesTo();
            OMElement relatesToHeader = null;

            if (relatesTo != null) {
                relatesToHeader =
                        processStringInfo(relatesTo.getValue(),
                                WSA_RELATES_TO,
                                soapHeader);
            }

            if (relatesToHeader != null)
                if ("".equals(relatesTo.getRelationshipType())) {
                    relatesToHeader.addAttribute(WSA_RELATES_TO_RELATIONSHIP_TYPE,
                            Submission.WSA_RELATES_TO_RELATIONSHIP_TYPE_DEFAULT_VALUE,
                            addressingNamespaceObject);
                } else {
                    relatesToHeader.addAttribute(WSA_RELATES_TO_RELATIONSHIP_TYPE,
                            relatesTo.getRelationshipType(),
                            addressingNamespaceObject);
                }
        }
    }


    private OMElement processStringInfo(String value,
                                        String type,
                                        SOAPHeader soapHeader) {
        if (!"".equals(value) && value != null) {
            SOAPHeaderBlock soapHeaderBlock =
                    soapHeader.addHeaderBlock(type, addressingNamespaceObject);
            soapHeaderBlock.addChild(
                    OMAbstractFactory.getOMFactory().createText(value));
            return soapHeaderBlock;
        }
        return null;
    }

    protected void addToSOAPHeader(EndpointReference epr,
                                   String type,
                                   SOAPHeader soapHeader) {
        if (epr == null || isAddressingHeaderAlreadyAvailable(type, soapHeader)) {
            return;
        }

        SOAPHeaderBlock soapHeaderBlock =
                soapHeader.addHeaderBlock(type, addressingNamespaceObject);

        String address = epr.getAddress();
        if (!"".equals(address) && address != null) {
            OMElement addressElement =
                    OMAbstractFactory.getOMFactory().createOMElement(
                            EPR_ADDRESS,
                            addressingNamespaceObject);
            soapHeaderBlock.addChild(addressElement);
            addressElement.setText(address);
        }

        addToHeader(epr, soapHeaderBlock);


        AnyContentType referenceParameters = epr.getReferenceParameters();
        if (referenceParameters != null) {
            OMElement reference =
                    OMAbstractFactory.getOMFactory().createOMElement(
                            EPR_REFERENCE_PARAMETERS,
                            addressingNamespaceObject);
            soapHeaderBlock.addChild(reference);
            processAnyContentType(referenceParameters, reference);

        }

        if (Submission.WSA_NAMESPACE.equals(addressingNamespace)) {
            AnyContentType referenceProperties = epr.getReferenceProperties();
            if (referenceProperties != null) {
                OMElement reference =
                        OMAbstractFactory.getOMFactory().createOMElement(
                                Submission.EPR_REFERENCE_PROPERTIES,
                                addressingNamespaceObject);
                soapHeader.addChild(reference);
                processAnyContentType(referenceParameters, reference);
            }

        }

    }

    private void addToHeader(EndpointReference epr, OMElement parentElement) {


        QName interfaceQName = epr.getInterfaceName();
        if (interfaceQName != null) {
            OMElement interfaceName =
                    OMAbstractFactory.getOMFactory().createOMElement(
                            addressingNamespace.equals(
                                    Submission.WSA_NAMESPACE) ?
                                    Submission.EPR_PORT_TYPE : Final.WSA_INTERFACE_NAME,
                            addressingNamespaceObject);
            interfaceName.addChild(
                    OMAbstractFactory.getOMFactory().createText(
                            interfaceQName.getPrefix() + ":" +
                                    interfaceQName.getLocalPart()));
            parentElement.addChild(interfaceName);
        }

        ServiceName serviceName = epr.getServiceName();
        if (serviceName != null) {
            OMElement serviceNameElement =
                    OMAbstractFactory.getOMFactory().createOMElement(
                            EPR_SERVICE_NAME,
                            addressingNamespaceObject);
            serviceNameElement.addAttribute(
                    addressingNamespace.equals(Submission.WSA_NAMESPACE) ?
                            Submission.EPR_SERVICE_NAME_PORT_NAME :
                            Final.WSA_SERVICE_NAME_ENDPOINT_NAME,
                    serviceName.getEndpointName(),
                    addressingNamespaceObject);
            serviceNameElement.addChild(
                    OMAbstractFactory.getOMFactory().createText(
                            serviceName.getName().getPrefix()
                                    + ":"
                                    + serviceName.getName().getLocalPart()));
            parentElement.addChild(serviceNameElement);
        }


    }


    private void processAnyContentType
            (AnyContentType
                    referenceValues,
             OMElement
                     parentElement) {
        if (referenceValues != null) {
            Iterator iterator = referenceValues.getKeys();
            while (iterator.hasNext()) {
                QName key = (QName) iterator.next();
                String value = referenceValues.getReferenceValue(key);
                OMElement omElement =
                        OMAbstractFactory.getOMFactory().createOMElement(key,
                                parentElement);
                if (Final.WSA_NAMESPACE.equals(addressingNamespace)) {
                    omElement.addAttribute(
                            Final.WSA_IS_REFERENCE_PARAMETER_ATTRIBUTE,
                            Final.WSA_TYPE_ATTRIBUTE_VALUE,
                            addressingNamespaceObject);

                }
                omElement.setText(value);
            }
        }
    }

    private boolean isAddressingHeaderAlreadyAvailable(String name, SOAPHeader soapHeader) {
        return soapHeader.getFirstChildWithName(new QName(addressingNamespaceObject.getName(), name, addressingNamespaceObject.getPrefix())) != null;
    }
}

