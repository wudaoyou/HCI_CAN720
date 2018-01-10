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
	String sequence;
	String startDate;
} 

class Param {
	String Pernr;
	String Bukrs;
	String Country;
	String ZzWorkcenter;
	String Stell;
	String ZzLocType;
	String locSubtype;
	String Zpersarea;
	String Btrtl;
	String Trfar;
	String Trfgb;
	String Trfgr;
	String Trfst;
	String Persg;
	String Persk;
	String Begda;
	String Endda;
	String Lgart;
	String Betrg;
	String pay_seqno;
	String pay_startdt;
}

def Message processData(Message message) {
	
	//Body
	String inXML = message.getBody(java.lang.String) as String;
	def body = new XmlSlurper().parseText(inXML);
	
	//Headers
	def map = message.getProperties();
	
	// Logs
	def messageLog = messageLogFactory.getMessageLog(message);
	
	String enableLogging = map.get("ENABLE_LOGGING");	
	if(enableLogging != null && enableLogging.toUpperCase().equals("TRUE")){				
		if(messageLog != null){
			messageLog.addAttachmentAsString("01 From EC", inXML, "text/xml");
		}
	}
	
	// beging mapping
	def compoundEEs = body.CompoundEmployee;
	
	String property = map.get("PERSON_ID_EXTERNAL_PARAMETER");	
	def pernrs = property.split(",");
	
	property = map.get("DATES");
	def dates = property.split(",");
	
	property = map.get("SEQUENCES");
	def seqs = property.split(",");
	
	property = map.get("USERIDS");
	def userids = property.split(",");
	
	String payload = "";
	
	HashMap eventMap = new HashMap(); 
	for (int i=0; i<pernrs.length; i++) {
		PushEvent event = new PushEvent();
		event.sequence = seqs[i].trim();
		event.startDate = dates[i].trim();
		eventMap.putAt(pernrs[i].trim(), event);
	}	
	
	compoundEEs.each {  
		String pernr = it.person.person_id_external;
		PushEvent event = eventMap.get(pernr);
		Param p = null;
		
		for (def emp in it.person.employment_information) {
			p = null;
			for (def job in emp.job_information) {
				String start_date = job.start_date;
				String seqno = job.seq_number;
				String empStatus = job.emplStatus;
				// check the start date and sequence number to make sure get the correct job_information
				if (start_date.equals(event.startDate) && seqno.equals(event.sequence) && empStatus == 'A') {
					// job_information found
					p = new Param();
					p.Pernr = pernr;
					p.Bukrs = job.company;
					p.Country = job.company_territory_code;
					p.Stell = job.job_code;
					p.ZzWorkcenter = job.location;
					p.ZzLocType = job.custom_string12;
					p.locSubtype = job.custom_string23;
					p.Zpersarea = job.custom_string15;
					p.Btrtl = job.custom_string16;
					p.Trfar = job.payScaleType;
					p.Trfgb = job.payScaleArea;
					p.Trfgr = job.payScaleGroup;
					p.Trfst = job.payScaleLevel;
					p.Persg = job.employee_class;
					p.Persk = job.employment_type;
					p.Begda = job.start_date;
					p.Endda = job.end_date;		
				}
			}// end of emp.job_information loop
			
			if (p != null) {
				for (def comp in emp.compensation_information) {
					for (def pay in comp.paycompensation_recurring) {
						String payStart = pay.start_date;
						String payEnd = pay.end_date;
						String wageType = pay.pay_component;
						if (payStart <= p.Begda && payEnd >= p.Endda) {
							p.Lgart = pay.pay_component;
							p.Betrg = pay.paycompvalue;
							p.pay_startdt = payStart;
							p.pay_seqno = pay.seq_number;
							if (wageType == "1010") break;				
						}
					}//end of paycompensation_recurring
				} // end of emp.compensation_information loop
                if (p.Lgart == "1010" || p.Lgart == "1005") {
					//the employee does not have wage type 1010
					p.Lgart = "1010"
					//p.pay_seqno = "1";
					p.pay_startdt = p.Begda;
					p.Betrg = 0;
				}

				if (p.Lgart == "1010") {
					// add pernr
					payload = payload + "<EmployeeNumber>" + p.Pernr + "</EmployeeNumber>";
					// add bukrs
					payload = payload + "<CompanyCode>" + p.Bukrs + "</CompanyCode>";
					// add country
					payload = payload + "<CountryCode>" + p.Country + "</CountryCode>";
					// add work center
					payload = payload + "<LocationCode>" + p.ZzWorkcenter + "</LocationCode>";
					// add stell
					payload = payload + "<JobCode>" + p.Stell + "</JobCode>";
					// add location type
					payload = payload + "<LocationType>" + p.ZzLocType + "</LocationType>";
					// add location sub type
					payload = payload + "<LocationSubType>" + p.locSubtype + "</LocationSubType>";
					// add persa
					payload = payload + "<PersonnelArea>" + p.Zpersarea + "</PersonnelArea>";
					// add Btrtl
					payload = payload + "<PersonnelSubArea>" + p.Btrtl + "</PersonnelSubArea>";
					// add pay scale parameters
					def v = p.Trfst.split("/");
					p.Trfst = v[v.length-1];
					v = p.Trfgr.split("/");
					p.Trfgr = v[v.length - 1];
					payload = payload + "<PayScaleType>" + p.Trfar + "</PayScaleType>";
					payload = payload + "<PayScaleArea>" + p.Trfgb + "</PayScaleArea>";
					payload = payload + "<PayScaleGroup>" + p.Trfgr + "</PayScaleGroup>";
					payload = payload + "<PayScaleLevel>" + p.Trfst + "</PayScaleLevel>";
					// add employee group/subgroup
					payload = payload + "<EmployeeGroup>" + p.Persg + "</EmployeeGroup>";
					payload = payload + "<EmployeeSubGroup>" + p.Persk + "</EmployeeSubGroup>";
					// add beg/end date
					payload = payload + "<BeginDate>" + p.Begda + "</BeginDate>";
					payload = payload + "<EndDate>" + p.Endda + "</EndDate>";
					// add wage info
					payload = payload + "<WageType>" + p.Lgart + "</WageType>";
					payload = payload + "<WageTypeAmount>" + p.Betrg + "</WageTypeAmount>";
					payload = payload + "<PaymentStartDate>" + p.pay_startdt + "</PaymentStartDate>";
					payload = payload + "<PaymentSequenceNumber>" + p.pay_seqno + "</PaymentSequenceNumber>";
				} // end of if lgart == 1010
			} // end of if p != null			
		}// end of employment information loop
	}// end of compoundEE loop
	
	
	
	message.setBody("<Request>" + payload + "</Request>");
	
	return message;
}

