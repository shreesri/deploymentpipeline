/*******************************************************************************
 *  * Copyright (c) 2017 AT&T Intellectual Property. All rights reserved. 
 *******************************************************************************/
package com.att.cicd.deploymentpipeline.workflow.notification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.SimpleEmail;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

import com.att.cicd.deploymentpipeline.service.UserInfoService;
import com.att.cicd.deploymentpipeline.util.AAFConnector;
import com.att.cicd.deploymentpipeline.workflow.dataaccess.ApplicationLogger;
import com.att.cicd.deploymentpipeline.workflow.dataaccess.Database;

public class PipelineFlowApprovalNotification implements JavaDelegate {
	
	public String getVar(DelegateExecution execution, String varName) {
		String var = (String) execution.getVariable(varName);
		if (var == null) {
			var = "";
		}
		return var;
	}
	
	public void execute(DelegateExecution ex) throws Exception {
		String recipient = getVar(ex, "assignTo");
		String gotsid = getVar(ex, "gotsid");
		String name = getVar(ex, "name");
		String processName = getVar(ex, "processName");
		String submoduleName = getVar(ex, "submodule_name");
		String pipelineId = getVar(ex, "pipeline_id");
		String pipelineName = getVar(ex, "pipeline_name");
		String acronym = getVar(ex, "acronym");
		String processDescription = getVar(ex, "processDescription");
		String emailDescription = Database.getDescription("emailDescription", pipelineName, submoduleName, processName);
		String deployment_indexer = getVar(ex, "deployment_indexer");
		String pipeline_flow_id = getVar(ex, "pipeline_flow_id");
		String deployConfigName = Database.getDeploymentConfigName(pipeline_flow_id, deployment_indexer);
		ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName, processName,
				"PipelineFlowApprovalNotification", "execute", "Beginning");
		
		mail(recipient, gotsid, name, processName, submoduleName, pipelineId, pipelineName, acronym, processDescription,
				emailDescription, deployConfigName);
		
		ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName, processName,
				"PipelineFlowApprovalNotification", "execute", "End");
	}
	
	private void mail(String recipient, String gotsid, String name, String processName, String submoduleName,
			String pipelineId, String pipelineName, String acronym, String processDescription, String emailDescription,
			String deployConfigName) {
		String emailBody = "";
		String emailFromAddress = System.getProperty("emailfromaddress");
		String emailHost = System.getProperty("emailhostname");
		String emailUsername = System.getProperty("emailusername");
		String emailPassword = System.getProperty("emailpassword");
		String emailSenderName = System.getProperty("emailsendername");
		String clientURL = System.getProperty("clienturl");
		int emailPort = Integer.valueOf(System.getProperty("emailsmtpport"));
		String emailSubject = "";
		List<String> recipients = new ArrayList<String>();
		boolean multipleRecipients = false;
		if (recipient == null || recipient.trim().isEmpty()) {
			String info = "There was no email address added, getting admin from aaf";
			ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName, processName,
					"ApprovalNotification", "mail", info);
			recipient = AAFConnector.getAdminList(gotsid);
		}
		if (recipient.contains(",")) {
			recipients = Arrays.asList(recipient.split("\\s*,\\s*"));
			multipleRecipients = true;
		} else if (recipient.contains(";")) {
			recipients = Arrays.asList(recipient.split("\\s*;\\s*"));
			multipleRecipients = true;
		}

		ResultSet rs = null;
		PreparedStatement ps = null;
		Connection con = Database.getConnection();
		String sql = "Select (SELECT attribute_value FROM camundabpmajsc6.system_config where attribute_name='emailbody') as emailBody, "
				+ "(SELECT attribute_value FROM camundabpmajsc6.system_config where attribute_name='emailSubject') as emailSubject;";
		try {
			ps = con.prepareStatement(sql);
			rs = ps.executeQuery();
			while (rs.next()) {
				;
				emailBody = rs.getString("emailBody");
				emailSubject = rs.getString("emailSubject");
			}
			Email email = new SimpleEmail();
			email.setHostName(emailHost);
			// email.setAuthentication(emailUsername, emailPassword);
			try {
				email.setFrom(emailFromAddress, emailSenderName);
				email.setSmtpPort(emailPort);

				if (multipleRecipients) {
					recipient = "";
					for (String person : recipients) {
						String personAddress = UserInfoService.getEmailAddress(person);
						if(!personAddress.equals(UserInfoService.NO_EMAIL)) {
							recipient += person + ", ";
							email.addTo(personAddress);
							String info = "Added " + personAddress + " to send the notification to.";
							ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName,
									processName, "ApprovalNotification", "mail", info);
						} else {
							String info = "No email detected. Not sending email to " + person;
							ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName,
									processName, "ApprovalNotification", "mail", info);
						}
					}
					String info = "The \"" + processName + "\" step is awaiting approval from: " + recipient.substring(0, recipient.length()-2);
					ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName, processName,
							"ApprovalNotification", "execute", info, true);
					info = "Sending Approval Email notification to: " + recipient.substring(0, recipient.length()-2);
					ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName, processName,
							"ApprovalNotification", "execute", info, true);
				} else {
					String person = UserInfoService.getEmailAddress(recipient);
					if(!person.equals(UserInfoService.NO_EMAIL)) {
						String info = "Added " + person + " to send the notification to.";
						ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName, processName,
								"ApprovalNotification", "mail", info);
						email.addTo(person);
						recipient = recipient + ", ";
					} else {
						String info = "No email detected. Not sending email to " + recipient;
						ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName, processName,
								"ApprovalNotification", "mail", info);
					}
					String info = "The \"" + processName + "\" step is awaiting approval from: " + recipient.substring(0, recipient.length()-2);
					ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName, processName,
							"ApprovalNotification", "execute", info, true);
					info = "Approval Email notification(s) have been sent to: " + recipient.substring(0, recipient.length()-2);
					ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName, processName,
							"ApprovalNotification", "execute", info, true);
				}
				emailSubject = emailSubject.replaceAll(Pattern.quote("((gotsid))"), gotsid);
				emailSubject = emailSubject.replaceAll(Pattern.quote("((emailTo))"), recipient);
				emailSubject = emailSubject.replaceAll(Pattern.quote("((name))"), name);
				emailSubject = emailSubject.replaceAll(Pattern.quote("((processName))"), processName);
				emailSubject = emailSubject.replaceAll(Pattern.quote("((submoduleName))"), submoduleName);
				emailSubject = emailSubject.replaceAll(Pattern.quote("((pipelineId))"), pipelineId);
				emailSubject = emailSubject.replaceAll(Pattern.quote("((acronym))"), acronym);
				emailSubject = emailSubject.replaceAll(Pattern.quote("((processDescription))"), processDescription);
				emailSubject = emailSubject.replaceAll(Pattern.quote("((emailDescription))"), emailDescription);
				emailSubject = emailSubject.replaceAll(Pattern.quote("((clientURL))"), clientURL);
				emailSubject = emailSubject.replaceAll(Pattern.quote("((deploymentName))"), deployConfigName);

				emailBody = emailBody.replaceAll(Pattern.quote("((emailTo))"), recipient);
				emailBody = emailBody.replaceAll(Pattern.quote("((gotsid))"), gotsid);
				emailBody = emailBody.replaceAll(Pattern.quote("((name))"), name);
				emailBody = emailBody.replaceAll(Pattern.quote("((processName))"), processName);
				emailBody = emailBody.replaceAll(Pattern.quote("((submoduleName))"), submoduleName);
				emailBody = emailBody.replaceAll(Pattern.quote("((pipelineId))"), pipelineId);
				emailBody = emailBody.replaceAll(Pattern.quote("((acronym))"), acronym);
				emailBody = emailBody.replaceAll(Pattern.quote("((processDescription))"), processDescription);
				emailBody = emailBody.replaceAll(Pattern.quote("((emailDescription))"), emailDescription);
				emailBody = emailBody.replaceAll(Pattern.quote("((clientURL))"), clientURL);
				emailBody = emailBody.replaceAll(Pattern.quote("((deploymentName))"), deployConfigName);

				email.setSubject(emailSubject);
				email.setContent(emailBody, "text/html; charset=utf-8");

				String protocol = System.getProperty("emailprotcol");
				protocol = protocol.toUpperCase();
				switch(protocol) {
				case "SSL":
					email.setSSLOnConnect(true);
					break;
				case "TLS":
					email.setStartTLSEnabled(true);
					email.setStartTLSRequired(true);
					break;
				case "SSLTLS":
					email.setSSLOnConnect(true);
					email.setStartTLSEnabled(true);
					email.setStartTLSRequired(true);
					break;
				case "NONE":
					//do nothing
					break;
				default:
					//default to do nothing. If this changes, update system.properties comment
				}

				email.setAuthenticator(new DefaultAuthenticator(emailUsername, emailPassword));
				email.send();
				String info = "Email sent successfully.....";
				ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName, processName,
						"ApprovalNotification", "mail", info);
			} catch (Exception e) {
				String info = "Invalid Email Address.";
				ApplicationLogger.callLog(gotsid, name, pipelineId, pipelineName, submoduleName, processName,
						"ApprovalNotification", "mail", info);
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
				if (ps != null) {
					ps.close();
				}
				if (con != null) {
					con.close();
				}

			} catch (Exception ex) {
			}
		}
	}
}
