/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013, 6WIND S.A. All rights reserved.                 *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources.actions;

import hudson.Extension;
import hudson.model.RootAction;
import hudson.model.User;
import hudson.model.Run;
import hudson.security.AccessDeniedException2;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkins.plugins.lockableresources.LockableResource;
import org.jenkins.plugins.lockableresources.LockableResourcesManager;
import org.jenkins.plugins.lockableresources.Messages;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
public class LockableResourcesRootAction implements RootAction {

	public static final PermissionGroup PERMISSIONS_GROUP = new PermissionGroup(
			LockableResourcesManager.class, Messages._LockableResourcesRootAction_PermissionGroup());
	public static final Permission UNLOCK = new Permission(PERMISSIONS_GROUP,
			Messages.LockableResourcesRootAction_UnlockPermission(),
			Messages._LockableResourcesRootAction_UnlockPermission_Description(), Jenkins.ADMINISTER,
			PermissionScope.JENKINS);
	public static final Permission RESERVE = new Permission(PERMISSIONS_GROUP,
			Messages.LockableResourcesRootAction_ReservePermission(),
			Messages._LockableResourcesRootAction_ReservePermission_Description(), Jenkins.ADMINISTER,
			PermissionScope.JENKINS);

	public static final Permission VIEW = new Permission(PERMISSIONS_GROUP,
			Messages.LockableResourcesRootAction_ViewPermission(),
			Messages._LockableResourcesRootAction_ViewPermission_Description(), Jenkins.ADMINISTER,
			PermissionScope.JENKINS);

	public static final String ICON = "/plugin/lockable-resources/img/device-24x24.png";

	@Override
	public String getIconFileName() {
		return Jenkins.get().hasPermission(VIEW) ? ICON : null;
	}

	public String getUserName() {
		User current = User.current();
		if (current != null)
			return current.getFullName();
		else
			return null;
	}

	@Override
	public String getDisplayName() {
	  return Messages.LockableResourcesRootAction_PermissionGroup();
	}

	@Override
	public String getUrlName() {
		return Jenkins.get().hasPermission(VIEW) ? "lockable-resources" : "";
	}

	public List<LockableResource> getResources() {
		return LockableResourcesManager.get().getResources();
	}

	public int getFreeResourceAmount(String label) {
		return LockableResourcesManager.get().getFreeResourceAmount(label);
	}

	public Set<String> getAllLabels() {
		return LockableResourcesManager.get().getAllLabels();
	}

	public int getNumberOfAllLabels() {
		return LockableResourcesManager.get().getAllLabels().size();
	}

	private Run getJenkinsBuild(String job, String build) {
		Jenkins jenkins = Jenkins.get();
		if (job == null || job.trim().isEmpty() || build == null || build.trim().isEmpty())
			return null;

		WorkflowJob jenkinsJob = (WorkflowJob) jenkins.getItem(job);
		if (jenkinsJob == null)
			return null;
		return (Run) jenkinsJob.getBuildByNumber(Integer.parseInt(build));
	}

	private boolean reserveResource(LockableResource resource, String message) {
		boolean status = false;
		String userName = getUserName();
		resource.setMessage(message);
		List<LockableResource> resources = new ArrayList<>();
		resources.add(resource);
		if (userName != null)
			status = LockableResourcesManager.get().reserve(resources, userName);
		return status;
	}

	private void unreserveResource(LockableResource resource) {
		List<LockableResource> resources = new ArrayList<>();
		resource.setMessage("");
		resources.add(resource);
		LockableResourcesManager.get().unreserve(resources);
	}

	private void unlockResource(LockableResource resource) {
		List<LockableResource> resources = new ArrayList<>();
		resource.setMessage("");
		resources.add(resource);
		LockableResourcesManager.get().unlock(resources, null);
	}

	@RequirePOST
	public void doLock(StaplerRequest req, StaplerResponse rsp)
		throws IOException, ServletException {
		Jenkins.get().checkPermission(UNLOCK);
		String name = req.getParameter("resource");
		String message = req.getParameter("message");
		String job = req.getParameter("job");
		String build = req.getParameter("build");
		LockableResource r = LockableResourcesManager.get().fromName(name);

		if (r == null) {
			rsp.sendError(404, "Resource not found " + name);
			return;
		}

		if (message == null || message.trim().length() < 3) {
			rsp.sendError(400, "Invalid message...!");
			return;
		}
		Run jenkinsBuild = getJenkinsBuild(job, build);
		if (jenkinsBuild == null) {
			rsp.sendError(404, "Unable to find build...!");
			return;
		}
		Set<LockableResource> resources = new HashSet<LockableResource>();
		r.setMessage(message);
		resources.add(r);
		boolean status = LockableResourcesManager.get().lock(resources, jenkinsBuild, null);
		if (!status) {
			rsp.sendError(409, "Requested resource is in use...!");
			return;
		}
		rsp.forwardToPreviousPage(req);
	}

	@RequirePOST
	public void doUnlock(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException {
		Jenkins.get().checkPermission(UNLOCK);

		String name = req.getParameter("resource");
		LockableResource r = LockableResourcesManager.get().fromName(name);
		if (r == null) {
			rsp.sendError(404, "Resource not found " + name);
			return;
		}
		unlockResource(r);
		rsp.forwardToPreviousPage(req);
	}

	@RequirePOST
	public void doReserve(StaplerRequest req, StaplerResponse rsp)
		throws IOException, ServletException {
		Jenkins.get().checkPermission(RESERVE);
		String name = req.getParameter("resource");
		String message = req.getParameter("message");
		LockableResource r = LockableResourcesManager.get().fromName(name);

		if (r == null) {
			rsp.sendError(404, "Resource not found " + name);
			return;
		}
		if (message == null || message.trim().length() < 3) {
			rsp.sendError(400, "Invalid message...!");
			return;
		}

		boolean status = reserveResource(r, message);
		if (!status) {
			rsp.sendError(409, "Requested resource is in use...!");
			return;
		}
		rsp.forwardToPreviousPage(req);
	}

	@RequirePOST
	public void doUnreserve(StaplerRequest req, StaplerResponse rsp)
		throws IOException, ServletException {
		Jenkins.get().checkPermission(RESERVE);

		String name = req.getParameter("resource");
		LockableResource r = LockableResourcesManager.get().fromName(name);
		if (r == null) {
			rsp.sendError(404, "Resource not found " + name);
			return;
		}

		String userName = getUserName();
		if ((userName == null || !userName.equals(r.getReservedBy()))
				&& !Jenkins.get().hasPermission(Jenkins.ADMINISTER))
			throw new AccessDeniedException2(Jenkins.getAuthentication(),
					RESERVE);
		unreserveResource(r);
		rsp.forwardToPreviousPage(req);
	}

	@RequirePOST
	public void doReset(StaplerRequest req, StaplerResponse rsp)
		throws IOException, ServletException {
		Jenkins.get().checkPermission(UNLOCK);

		String name = req.getParameter("resource");
		LockableResource r = LockableResourcesManager.get().fromName(name);
		if (r == null) {
			rsp.sendError(404, "Resource not found " + name);
			return;
		}

		List<LockableResource> resources = new ArrayList<>();
		r.setMessage("");
		resources.add(r);
		LockableResourcesManager.get().reset(resources);

		rsp.forwardToPreviousPage(req);
	}

	@RequirePOST
	public void doAcquire(StaplerRequest req, StaplerResponse rsp)
		throws IOException, ServletException {
		Jenkins.get().checkPermission(RESERVE);
		String name = req.getParameter("resource");
		String label = req.getParameter("label");
		String message = req.getParameter("message");
		String job = req.getParameter("job");
		String build = req.getParameter("build");

		LockableResource lr = null;

		if (message == null || message.trim().length() < 3) {
			rsp.sendError(400, "Invalid message...!");
			return;
		} else if (name != null && !name.trim().isEmpty()) {
			lr = LockableResourcesManager.get().fromName(name);
			if (lr == null) {
				rsp.sendError(404, "Resource not found " + name);
				return;
			}
			if (lr.isReserved()) {
				String userName = getUserName();
				if (userName == null || !userName.equals(lr.getReservedBy())) {
					rsp.sendError(401, "Unauthorized to reserve...!");
					return;
				}
			} else {
				boolean status = reserveResource(lr, message);
				if (!status) {
					rsp.sendError(409, "Unable to reserve the resource...!");
					return;
				}
			}
		} else if (label != null && !label.trim().isEmpty()) {
			Run jenkinsBuild = getJenkinsBuild(job, build);
			if (jenkinsBuild != null) {
				lr = LockableResourcesManager.get().lockFreeResource(
						label, jenkinsBuild, message);
			} else {
				String userName = getUserName();
				lr = LockableResourcesManager.get().reserveFreeResource(
						label, message, userName);
			}
		} else {
			rsp.sendError(400, "Invalid parameters....!");
			return;
		}
		if (lr == null) {
			rsp.sendError(404, "Resource not available....!");
			return;
		}

		rsp.setContentType("application/json");
		rsp.setHeader("Cache-Control", "no-cache, no-store, no-transform");
		rsp.getWriter().write(lr.getName());
	}

	@RequirePOST
	public void doRelease(StaplerRequest req, StaplerResponse rsp)
		throws IOException, ServletException {
		Jenkins.get().checkPermission(RESERVE);
		String name = req.getParameter("resource");
		LockableResource lr = LockableResourcesManager.get().fromName(name);
		if (lr == null) {
			rsp.sendError(404, "Resource not found " + name);
			return;
		}
		if (lr.isReserved()) {
			String userName = getUserName();
			if (userName == null || !userName.equals(lr.getReservedBy())) {
				rsp.sendError(401, "Unauthorized to unreserve...!");
				return;
			}
			unreserveResource(lr);
		} else if (lr.isLocked()) {
			String job = req.getParameter("job");
			String build = req.getParameter("build");
			String buildName = lr.getBuildName();
			Run jenkinsBuild = getJenkinsBuild(job, build);
			if (jenkinsBuild == null || !buildName.equals(jenkinsBuild.getFullDisplayName())) {
				rsp.sendError(401, "Unauthorized to unlock...!");
				return;
			}
			unlockResource(lr);
		} else {
			rsp.sendError(400, "Resource is not locked/reserved...!");
			return;
		}
		rsp.setContentType("application/json");
		rsp.setHeader("Cache-Control", "no-cache, no-store, no-transform");
		rsp.getWriter().write("true");
	}

	@RequirePOST
	public void doUpdateMessage(StaplerRequest req, StaplerResponse rsp)
		throws IOException, ServletException {
		Jenkins.get().checkPermission(RESERVE);
		String name = req.getParameter("resource");
		String message = req.getParameter("message");
		if (message == null || message.trim().length() < 3) {
			rsp.sendError(400, "Invalid message...!");
			return;
		}
		LockableResource lr = LockableResourcesManager.get().fromName(name);
		if (lr == null) {
			rsp.sendError(404, "Resource not found " + name);
			return;
		}
		LockableResourcesManager.get().updateMessage(lr, message);
	}
}
