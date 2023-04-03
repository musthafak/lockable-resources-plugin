/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013, 6WIND S.A. All rights reserved.                 *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources.actions;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Api;
import hudson.model.RootAction;
import hudson.model.Run;
import hudson.security.AccessDeniedException3;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.json.JSONObject;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkins.plugins.lockableresources.queue.LockableResourcesStruct;
import org.jenkins.plugins.lockableresources.queue.QueuedContextStruct;
import org.jenkins.plugins.lockableresources.LockableResource;
import org.jenkins.plugins.lockableresources.LockableResourcesManager;
import org.jenkins.plugins.lockableresources.Messages;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
@ExportedBean
public class LockableResourcesRootAction implements RootAction {

  public static final PermissionGroup PERMISSIONS_GROUP = new PermissionGroup(
      LockableResourcesManager.class, Messages._LockableResourcesRootAction_PermissionGroup());
  public static final Permission UNLOCK = new Permission(
      PERMISSIONS_GROUP,
      Messages.LockableResourcesRootAction_UnlockPermission(),
      Messages._LockableResourcesRootAction_UnlockPermission_Description(),
      Jenkins.ADMINISTER,
      PermissionScope.JENKINS);
  public static final Permission RESERVE = new Permission(
      PERMISSIONS_GROUP,
      Messages.LockableResourcesRootAction_ReservePermission(),
      Messages._LockableResourcesRootAction_ReservePermission_Description(),
      Jenkins.ADMINISTER,
      PermissionScope.JENKINS);
  public static final Permission STEAL = new Permission(
      PERMISSIONS_GROUP,
      Messages.LockableResourcesRootAction_StealPermission(),
      Messages._LockableResourcesRootAction_StealPermission_Description(),
      Jenkins.ADMINISTER,
      PermissionScope.JENKINS);
  public static final Permission VIEW = new Permission(
      PERMISSIONS_GROUP,
      Messages.LockableResourcesRootAction_ViewPermission(),
      Messages._LockableResourcesRootAction_ViewPermission_Description(),
      Jenkins.ADMINISTER,
      PermissionScope.JENKINS);

  public static final String ICON = "symbol-lock-closed";

  @Override
  public String getIconFileName() {
    return Jenkins.get().hasPermission(VIEW) ? ICON : null;
  }

  public Api getApi() {
    return new Api(this);
  }

  @CheckForNull
  public String getUserName() {
    return LockableResource.getUserName();
  }

  @Override
  public String getDisplayName() {
    return Messages.LockableResourcesRootAction_PermissionGroup();
  }

  @Override
  public String getUrlName() {
    return Jenkins.get().hasPermission(VIEW) ? "lockable-resources" : "";
  }

  @Exported
  public List<LockableResource> getResources() {
    return LockableResourcesManager.get().getResources();
  }

  public LockableResource getResource(final String resourceName) {
    return LockableResourcesManager.get().fromName(resourceName);
  }

  /**
   * Get amount of free resources assigned to given *label*
   *
   * @param label Label to search.
   * @return Amount of free labels.
   */
  public int getFreeResourceAmount(String label) {
    return LockableResourcesManager.get().getFreeResourceAmount(label);
  }

  /**
   * Get percentage (0-100) usage of resources assigned to given *label*
   *
   * Used by {@code actions/LockableResourcesRootAction/index.jelly}
   *
   * @since 2.19
   * @param label Label to search.
   * @return Percentage usages of *label* around all resources
   */
  @Restricted(NoExternalUse.class)
  public int getFreeResourcePercentage(String label) {
    final int allCount = this.getAssignedResourceAmount(label);
    if (allCount == 0) {
      return allCount;
    }
    return (int) ((double) this.getFreeResourceAmount(label) / (double) allCount * 100);
  }

  /**
   * Get all existing labels as list.
   *
   * @return All possible labels.
   */
  public Set<String> getAllLabels() {
    return LockableResourcesManager.get().getAllLabels();
  }

  /**
   * Get amount of all labels.
   *
   * @return Amount of all labels.
   */
  public int getNumberOfAllLabels() {
    return LockableResourcesManager.get().getAllLabels().size();
  }

  private Run<WorkflowJob, WorkflowRun> getJenkinsBuild(String job, String build) {
    Jenkins jenkins = Jenkins.get();
    if (job == null || job.trim().isEmpty() || build == null || build.trim().isEmpty())
      return null;

    WorkflowJob jenkinsJob = (WorkflowJob) jenkins.getItemByFullName(job);
    if (jenkinsJob == null)
      return null;
    return (Run<WorkflowJob, WorkflowRun>) jenkinsJob.getBuildByNumber(Integer.parseInt(build));
  }

  private void updateNote(List<LockableResource> resources, String note) {
    for (LockableResource resource : resources) {
      resource.setNote(note);
    }
  }

  /**
   * Get amount of resources assigned to given *label*
   *
   * Used by {@code actions/LockableResourcesRootAction/index.jelly}
   *
   * @param label Label to search.
   * @return Amount of assigned resources.
   */
  @Restricted(NoExternalUse.class)
  public int getAssignedResourceAmount(String label) {
    return LockableResourcesManager.get().getResourcesWithLabel(label, null).size();
  }

  /** Returns current queue */
  @Restricted(NoExternalUse.class) // used by jelly
  public List<QueuedContextStruct> getCurrentQueuedContext() {
    return LockableResourcesManager.get().getCurrentQueuedContext();
  }

  /** Returns current queue */
  @Restricted(NoExternalUse.class) // used by jelly
  @CheckForNull
  public LockableResourcesStruct getOldestQueue() {
    LockableResourcesStruct oldest = null;
    for (QueuedContextStruct context : this.getCurrentQueuedContext()) {
      for (LockableResourcesStruct resourceStruct : context.getResources()) {
        if (resourceStruct.queuedAt == 0) {
          // Older versions of this plugin might miss this information.
          // Therefore skip it here.
          continue;
        }
        if (oldest == null || oldest.queuedAt > resourceStruct.queuedAt) {
          oldest = resourceStruct;
        }
      }
    }
    return oldest;
  }

  @RequirePOST
  public void doUnlock(StaplerRequest req, StaplerResponse rsp)
      throws IOException, ServletException {
    Jenkins.get().checkPermission(UNLOCK);

    List<LockableResource> resources = this.getResourcesFromRequest(req, rsp);
    if (resources == null) {
      return;
    }

    LockableResourcesManager.get().unlock(resources, null);
    updateNote(resources, "");

    rsp.forwardToPreviousPage(req);
  }

  @RequirePOST
  public void doReserve(StaplerRequest req, StaplerResponse rsp)
      throws IOException, ServletException {
    Jenkins.get().checkPermission(RESERVE);

    List<LockableResource> resources = this.getResourcesFromRequest(req, rsp);
    if (resources == null) {
      return;
    }

    String userName = getUserName();
    if (userName != null) {
      if (!LockableResourcesManager.get().reserve(resources, userName)) {
        rsp.sendError(423, Messages.error_resourceAlreadyLocked(LockableResourcesManager.getResourcesNames(resources)));
        return;
      }
    }
    updateNote(resources, "");
    rsp.forwardToPreviousPage(req);
  }

  @RequirePOST
  public void doSteal(StaplerRequest req, StaplerResponse rsp)
      throws IOException, ServletException {
    Jenkins.get().checkPermission(STEAL);

    List<LockableResource> resources = this.getResourcesFromRequest(req, rsp);
    if (resources == null) {
      return;
    }

    String userName = getUserName();
    if (userName != null) {
      LockableResourcesManager.get().steal(resources, userName);
    }

    rsp.forwardToPreviousPage(req);
  }

  @RequirePOST
  public void doReassign(StaplerRequest req, StaplerResponse rsp)
      throws IOException, ServletException {
    Jenkins.get().checkPermission(STEAL);

    String userName = getUserName();
    if (userName == null) {
      // defensive: this can not happens because we check you permissions few lines
      // before
      // therefore you must be logged in
      throw new AccessDeniedException3(Jenkins.getAuthentication2(), STEAL);
    }

    List<LockableResource> resources = this.getResourcesFromRequest(req, rsp);
    if (resources == null) {
      return;
    }

    for (LockableResource resource : resources) {
      if (userName.equals(resource.getReservedBy())) {
        // Can not achieve much by re-assigning the
        // resource I already hold to myself again,
        // that would just burn the compute resources.
        // ...unless something catches the event? (TODO?)
        return;
      }
    }

    LockableResourcesManager.get().reassign(resources, userName);

    rsp.forwardToPreviousPage(req);
  }

  @RequirePOST
  public void doUnreserve(StaplerRequest req, StaplerResponse rsp)
      throws IOException, ServletException {
    Jenkins.get().checkPermission(RESERVE);

    List<LockableResource> resources = this.getResourcesFromRequest(req, rsp);
    if (resources == null) {
      return;
    }

    String userName = getUserName();
    for (LockableResource resource : resources) {
      if ((userName == null || !userName.equals(resource.getReservedBy()))
          && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
        throw new AccessDeniedException3(Jenkins.getAuthentication2(), RESERVE);
      }
    }

    LockableResourcesManager.get().unreserve(resources);
    updateNote(resources, "");

    rsp.forwardToPreviousPage(req);
  }

  @RequirePOST
  public void doReset(StaplerRequest req, StaplerResponse rsp)
      throws IOException, ServletException {
    Jenkins.get().checkPermission(UNLOCK);
    // Should this also be permitted by "STEAL"?..

    List<LockableResource> resources = this.getResourcesFromRequest(req, rsp);
    if (resources == null) {
      return;
    }

    LockableResourcesManager.get().reset(resources);
    updateNote(resources, "");

    rsp.forwardToPreviousPage(req);
  }

  @RequirePOST
  public void doSaveNote(final StaplerRequest req, final StaplerResponse rsp)
      throws IOException, ServletException {
    Jenkins.get().checkPermission(RESERVE);

    String resourceName = req.getParameter("resource");
    if (resourceName == null) {
      resourceName = req.getParameter("resourceName");
    }

    final LockableResource resource = getResource(resourceName);
    if (resource == null) {
      rsp.sendError(404, Messages.error_resourceDoesNotExist(resourceName));
    } else {
      String resourceNote = req.getParameter("note");
      if (resourceNote == null) {
        resourceNote = req.getParameter("resourceNote");
      }
      resource.setNote(resourceNote);
      LockableResourcesManager.get().save();

      rsp.forwardToPreviousPage(req);
    }
  }

  private List<LockableResource> getResourcesFromRequest(final StaplerRequest req, final StaplerResponse rsp)
      throws IOException, ServletException {
    // todo, when you try to improve the API to use multiple resources (a list
    // instead of single one)
    // this will be the best place to change it. Probably it will be enough to add a
    // code piece here
    // like req.getParameter("resources"); And split the content by some delimiter
    // like ' ' (space)
    String name = req.getParameter("resource");
    LockableResource r = LockableResourcesManager.get().fromName(name);
    if (r == null) {
      rsp.sendError(404, Messages.error_resourceDoesNotExist(name));
      return null;
    }

    List<LockableResource> resources = new ArrayList<>();
    resources.add(r);
    return resources;
  }

  @RequirePOST
  public void doAcquire(StaplerRequest req, StaplerResponse rsp)
      throws IOException, ServletException {
    Jenkins.get().checkPermission(RESERVE);
    String name = req.getParameter("resource");
    String job = req.getParameter("job");
    String build = req.getParameter("build");

    LockableResource lr = null;

    if (name != null && !name.trim().isEmpty()) {
      lr = LockableResourcesManager.get().fromName(name);
      if (lr == null) {
        rsp.sendError(404, "Resource not found " + name);
        return;
      }
      if (lr.isReserved()) {
        String userName = getUserName();
        if (userName == null || !userName.equals(lr.getReservedBy())) {
          rsp.sendError(401, "Unauthorized to use the resource...!");
          return;
        }
      } else if (lr.isLocked()) {
        boolean buildMatches = false;
        String buildName = lr.getBuildName();
        Run<WorkflowJob, WorkflowRun> jenkinsBuild = getJenkinsBuild(job, build);
        if (jenkinsBuild != null) {
          if (buildName.equals(jenkinsBuild.getFullDisplayName())) {
            buildMatches = true;
          }
        }
        if (!buildMatches) {
          rsp.sendError(401, "Unauthorized to use the resource...!");
          return;
        }
      } else {
        rsp.sendError(400, "User must reserve the resource from Jenkins UI...!");
        return;
      }
    } else {
      rsp.sendError(400, "Invalid parameters....!");
      return;
    }

    JSONObject jo = new JSONObject();
    jo.put("resource", lr.getName());
    rsp.setContentType("application/json");
    rsp.setHeader("Cache-Control", "no-cache, no-store, no-transform");
    jo.write(rsp.getWriter());
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
    LockableResourcesManager.get().updateNote(lr, message);
  }
}
