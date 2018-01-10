import com.sap.gateway.ip.core.customdev.util.Message;
import java.util.HashMap;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.lang.Exception;
import groovy.xml.*;

def Message processData(Message message) {

	String inXML = message.getBody(java.lang.String) as String;
	def body = new XmlSlurper().parseText(inXML).declareNamespace(ns2:'http://notification.event.successfactors.com');

	//Properties
	def pmap = message.getProperties();
	def messageLog = messageLogFactory.getMessageLog(message);
	

	String enableLogging = pmap.get("ENABLE_LOGGING");
	String objectsSelectStatement = " person, employment_information, job_information, compensation_information, paycompensation_recurring";
	String personIdExternal;
	String dates;
	String seqs;
	String userids;

	//Set general object list for select statement
	message.setProperty("OBJECTS_SELECT_STATEMENT", objectsSelectStatement);

	//Error handling for external parameters
	if(enableLogging == null || (!enableLogging.equals("TRUE") && !enableLogging.equals("FALSE"))){
		throw new Exception("Configuration Error: Please enter either TRUE or FALSE in the parameter ENABLE_LOGGING.   ");
	}


	def events = body.events.event;
	events.each {
		personIdExternal = "";
		dates = "";
		seqs = "";
		userids = "";
		for (def key in it.entityKeys.entityKey) {
			String name = key.name;
			String value = key.value;
			if (name.equals("startDate")) {
				if (dates == "") {
					dates = value;
				} else {
					dates = dates + "," + value;
				}
			} else if (name.equals("seqNumber")) {
 				if (seqs == "") {
					 seqs = value;
				} else {
					seqs = seqs + "," + value;
				}
			} else if (name.equals("userId")) {
				if (userids == "") {
					userids = value;
				} else {
					userids = userids + "," + value;
				}
			}
		} 
		
		for (def param in it.params.param) {
			String name = param.name;
			String value = param.value;
			if (name.equals("personIdExternal")) {
				if (personIdExternal == "") {
					personIdExternal = value;
				} else {
					personIdExternal = personIdExternal + "," + value;
				}
			}
		}
		
	} // events loop

	message.setProperty("PERSON_ID_EXTERNAL_PARAMETER", personIdExternal);
	message.setProperty("DATES", dates);
	message.setProperty("SEQUENCES", seqs);
	message.setProperty("USERIDS", userids);
	

	if (messageLog != null) {
		messageLog.setStringProperty("Param 1 - PERNR ", personIdExternal);
		messageLog.setStringProperty("Param 2 - DATE ", dates);
		messageLog.setStringProperty("Param 3 - SEQ ", seqs);
		messageLog.setStringProperty("Param 4 - USERIDS ", userids);
	}
	
	if (personIdExternal == "") return message;
	  
	String query = "";
		  
	//Build dynamic where statement
	String queryPersonID = "";
	String queryLastModifiedDate = "";
	String company = pmap.get("COMPANY");
	String employeeClass = pmap.get("EMPLOYEE_CLASS");

	//If person_id_external filter parameter is set use it
	if(personIdExternal != null && !personIdExternal.equals("")){
		
		// add multi-valued selection parameter for person id to where clause
		personIdExternal = personIdExternal.replaceAll(",", "', '");
		queryPersonID = "person_id_external IN ('" + personIdExternal + "')";
	
		query = queryPersonID;
	}
	
	
	if(messageLog != null){
		messageLog.setStringProperty("PERSON_ID_EXTERNAL_PARAMETER ", queryPersonID);
	}
	
	// add multi-valued selection parameter for company to where clause
	// format: ... IN ('Company1','Company2')
	if (company != null && !company.equals("") && !company.equals("<company>")) {
		company_split = company.split(",");
		for (int j=0; j < company_split.length; j++) {
			if (j==0)
				company = "'" + company_split[j].trim() + "'";
			else
				company = company + ",'" + company_split[j].trim() + "'";
		}
		if (!query.equals("")) {
			query = query + " AND ";
		}
		query = query + "company IN (" + company + ")";
	}
	
	// add multi-valued selection parameter for employee class to where clause
	// format: ... IN ('employeeClass1','employeeClass2')
	if(employeeClass != null && !employeeClass.equals("") && !employeeClass.equals("<employee_class>")) {
		employeeClass_split = employeeClass.split(",");
		for (int k=0; k < employeeClass_split.length; k++) {
			if (k==0)
				employeeClass = "'" + employeeClass_split[k].trim() + "'";
			else
				employeeClass = employeeClass + ",'" + employeeClass_split[k].trim() + "'";
		}
		if (!query.equals("")) {
			query = query + " AND ";
		}
		query = query + "employee_class IN (" + employeeClass + ")";
	}
		

	//Set FILTER_PARAMETERS
	message.setProperty("FILTER_PARAMETERS", query);
	if(messageLog != null){
		messageLog.setStringProperty("FILTER_PARAMETERS: ", query);
	}

	//*** Set SFAPI Parameters ***
	String SFAPIParameters = "";
	SFAPIParameters = "resultOptions=allJobChangesPerDay";

	message.setProperty("SFAPI_PARAMETERS", SFAPIParameters);
	
	return message;
}

