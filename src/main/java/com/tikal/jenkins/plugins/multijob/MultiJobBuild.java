package com.tikal.jenkins.plugins.multijob;

import hudson.model.Action;
import hudson.model.BallColor;
import hudson.model.Build;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import java.io.File;
import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class MultiJobBuild extends Build<MultiJobProject, MultiJobBuild> {

	public MultiJobBuild(MultiJobProject project) throws IOException {
		super(project);
	}

	MultiJobChangeLogSet changeSets = new MultiJobChangeLogSet(this);

	@Override
	public ChangeLogSet<? extends Entry> getChangeSet() {
		return super.getChangeSet();
	}

	public void addChangeLogSet(ChangeLogSet<? extends Entry> changeLogSet) {
		this.changeSets.addChangeLogSet(changeLogSet);
	}

	public MultiJobBuild(MultiJobProject project, File buildDir)
			throws IOException {
		super(project, buildDir);
	}
	
	@Override
	public synchronized void doStop(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException {
		super.doStop(req, rsp);
	}

	@Override
	public void addAction(Action a) {
		super.addAction(a);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		run(new MultiJobRunnerImpl());
	}

	protected class MultiJobRunnerImpl extends
			Build<MultiJobProject, MultiJobBuild>.RunnerImpl {
		@Override
		public Result run(BuildListener listener) throws Exception {
			Result result = super.run(listener);
			if (isAborted())
				return Result.ABORTED;
			if (isFailure())
				return Result.FAILURE;
			if (isUnstable())
				return Result.UNSTABLE;
			return result;
		}

		private boolean isAborted() {
			return evaluateResult(Result.FAILURE);
		}
		
		private boolean isFailure() {
			return evaluateResult(Result.UNSTABLE);
		}

		private boolean isUnstable() {
			return evaluateResult(Result.SUCCESS);
		}

		private boolean evaluateResult(Result result) {
			List<SubBuild> builders = getBuilders();
			for (SubBuild subBuild : builders) {
				Result buildResult = subBuild.getResult();
				if (buildResult != null && buildResult.isWorseThan(result)) {
					return true;
				}
			}
			return false;
		}
	}

	public List<SubBuild> getBuilders() {
		MultiJobBuild multiJobBuild = getParent().getNearestBuild(getNumber());
		List<SubBuild> subBuilds = multiJobBuild.getSubBuilds();
		for (SubBuild subBuild : subBuilds) {
			Run build = getBuild(subBuild);
			if (build != null) {
				subBuild.setResult(build.getResult());
				subBuild.setIcon(build.getIconColor().getImage());
				subBuild.setDuration(build.getDurationString());
				subBuild.setUrl(build.getUrl());
                Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String date = formatter.format(build.getTimestamp().getTime());
                subBuild.setStartTimeString(date);
			} else {
				subBuild.setIcon(BallColor.NOTBUILT.getImage());
				subBuild.setDuration("not built yet");
				subBuild.setUrl(null);
			}
		}
		return subBuilds;
	}

	private Run getBuild(SubBuild subBuild) {
		Run build = null;

        // Get all of the downstream projects of this one
		List<AbstractProject> downstreamProjects = getProject()
				.getDownstreamProjects();
        // Iterate through downstream projects
		for (AbstractProject downstreamProject : downstreamProjects) {
            // get list of upstream projects for this downstream project
			List upstreamProjects = downstreamProject.getUpstreamProjects();
            // If one of the upstream projects is this one
			if (upstreamProjects.contains(getProject())) {
                // and it has the same name as the build im looking for
				if (subBuild.getJobName().equalsIgnoreCase(downstreamProject.getName())) {
					build = downstreamProject.getBuildByNumber(subBuild.getBuildNumber());
				}
			}
		}
		return build;
	}

	public void addSubBuild(String parentJobName, int parentBuildNumber,
			String jobName, int buildNumber, String phaseName,
			AbstractBuild refBuild) {
		SubBuild subBuild = new SubBuild(parentJobName, parentBuildNumber,
				jobName, buildNumber, phaseName);
		for (SubBuild subbuild : getSubBuilds()) {
            // Only remove the build from the sub builds if it is in the same phase
			if (subbuild.getJobName().equals(jobName) && subbuild.getPhaseName().equals(phaseName)) {
				getSubBuilds().remove(subbuild);
				break;
			}
		}
		getSubBuilds().add(subBuild);
	}

	private List<SubBuild> subBuilds;

	public List<SubBuild> getSubBuilds() {
		if (subBuilds == null)
			subBuilds = new ArrayList<SubBuild>();
		return subBuilds;
	}

	public static class SubBuild {

		private final String parentJobName;
		private final int parentBuildNumber;
		private final String jobName;
		private final int buildNumber;
		private final String phaseName;

		private Result result;
		private String icon;
		private String duration;
		private String url;
        private String startTimeString;

		public SubBuild(String parentJobName, int parentBuildNumber,
				String jobName, int buildNumber, String phaseName) {
			this.parentJobName = parentJobName;
			this.parentBuildNumber = parentBuildNumber;
			this.jobName = jobName;
			this.buildNumber = buildNumber;
			this.phaseName = phaseName;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public void setDuration(String duration) {
			this.duration = duration;
		}

		public void setIcon(String icon) {
			this.icon = icon;
		}

		public String getDuration() {
			return duration;
		}

		public String getIcon() {
			return icon;
		}

		public String getUrl() {
			return url;
		}

		public String getPhaseName() {
			return phaseName;
		}

		public String getParentJobName() {
			return parentJobName;
		}

		public int getParentBuildNumber() {
			return parentBuildNumber;
		}

		public String getJobName() {
			return jobName;
		}

		public int getBuildNumber() {
			return buildNumber;
		}

		public void setResult(Result result) {
			this.result = result;
		}

		public Result getResult() {
			return result;
		}

        public String getStartTimeString() {
            return startTimeString;
        }

        public void setStartTimeString(String startTimeString) {
            this.startTimeString = startTimeString;
        }

		@Override
		public String toString() {
			return "SubBuild [parentJobName=" + parentJobName
					+ ", parentBuildNumber=" + parentBuildNumber + ", jobName="
					+ jobName + ", buildNumber=" + buildNumber + "]";
		}
    }
}