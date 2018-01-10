/*
 * The integration developer needs to create the method processData 
 * This method takes Message object of package com.sap.gateway.ip.core.customdev.util
 * which includes helper methods useful for the content developer:
 * 
 * The methods available are:
    public java.lang.Object getBody()
    
    //This method helps User to retrieve message body as specific type ( InputStream , String , byte[] ) - e.g. message.getBody(java.io.InputStream)
    public java.lang.Object getBody(java.lang.String fullyQualifiedClassName)

    public void setBody(java.lang.Object exchangeBody)

    public java.util.Map<java.lang.String,java.lang.Object> getHeaders()

    public void setHeaders(java.util.Map<java.lang.String,java.lang.Object> exchangeHeaders)

    public void setHeader(java.lang.String name, java.lang.Object value)

    public java.util.Map<java.lang.String,java.lang.Object> getProperties()

    public void setProperties(java.util.Map<java.lang.String,java.lang.Object> exchangeProperties) 

	public void setProperty(java.lang.String name, java.lang.Object value)
 * 
 */
import com.sap.gateway.ip.core.customdev.util.Message;
import java.util.HashMap;
import groovy.xml.*;

class PushEvent {
	String userid;
	String pernr;
	String sequence;
	String startDate;
}

def Message processData(Message message) {
	
	String inXML = message.getBody(java.lang.String) as String;
	def body = new XmlSlurper().parseText(inXML).declareNamespace(env:'http://www.w3.org/2003/05/soap-envelope');
	body.declareNamespace(n0:'http://costco.com/svc/businessAdministrationAndFinancials/HumanResources/EmployeePremiumRate/v1/');
	
	//Headers
	def map = message.getProperties();
	
	// Logs
	def messageLog = messageLogFactory.getMessageLog(message);
	
	String enableLogging = map.get("ENABLE_LOGGING");	
	if(enableLogging != null && enableLogging.toUpperCase().equals("TRUE")){				
		if(messageLog != null){
			messageLog.addAttachmentAsString("03 From SAP ", inXML, "text/xml");
		}
	}
	
	// gather event information
	String property = map.get("PERSON_ID_EXTERNAL_PARAMETER");
	def pernrs = property.split(",");
	
	property = map.get("DATES");
	def dates = property.split(",");
	
	property = map.get("SEQUENCES");
	def seqs = property.split(",");

	property = map.get("USERIDS");
	def userids = property.split(",");
	
	def eventMap = new HashMap();
	
	for (int i=0; i<pernrs.length; i++) {
		PushEvent e = new PushEvent();
		e.pernr = pernrs[i];
		e.userid = userids[i];
		e.startDate = dates[i];
		e.sequence = seqs[i];
		eventMap.put(pernrs[i], e);
	}
	
	String payload = "";
	
	def results = body.'env:Body'.'n0:getResponse'.Response.OutgoingEmployeePremiumRate;;
	results.each {  
		PushEvent e = null;
		String pernr = it.EmployeeNumber;
		e = eventMap.get(pernr);
		String subrc = it.ErrorCode;
		if (subrc == "0") {
			// only update when subrc == 0, any none zero value means the call did not succeed
			String data;
			data = it.WageTypeAmount;
			
			if (Float.parseFloat(data) != 0.00) { 

				payload = payload + "<EmpPayCompRecurring>";
				
				payload = payload + "<paycompvalue>" + data.trim() + "</paycompvalue>"
				
				data = it.BeginDate;
				data = data.substring(0,4) + "-" + data.substring(4,6) + "-" + data.substring(6,8) + "T00:00:00.000";
				payload = payload + "<startDate>" + data + "</startDate>";
							
				payload = payload + "<payComponent>1010</payComponent>";
				payload = payload + "<userId>" + e.userid + "</userId>";
				
				data = it.PaymentSequenceNumber;
				payload = payload + "<seqNumber>" + data.trim() + "</seqNumber>";
			//	payload = payload + "<currencyCode>CAD</currencyCode>";
			//	payload = payload + "<frequency>HOURLY</frequency>";
				payload = payload + "</EmpPayCompRecurring>";
			}
		}
	}
	String test_mode = map.get("TEST_MODE");
	
	if (test_mode.equals("TRUE")) {
		String test_pernrs = map.get("TEST_PERNRS");	
		if (test_pernrs == "" || test_pernrs == null) {
			payload = "";
		} else {
			def EEs = test_pernrs.split(',');
			PushEvent e = null;
			for (def pernr in EEs) {
				e = eventMap.get(pernr);
				if (e != null) break;
			}
			if (e == null) payload = "";
		}
	} 
	
	message.setBody("<EmpPayCompRecurring>" + payload + "</EmpPayCompRecurring>");
	
	// Log upsert payload
	if(enableLogging != null && enableLogging.toUpperCase().equals("TRUE")){
		if(messageLog != null){
			messageLog.addAttachmentAsString("04 To EC ", payload, "text/xml");
		}
	}
	return message;
}

