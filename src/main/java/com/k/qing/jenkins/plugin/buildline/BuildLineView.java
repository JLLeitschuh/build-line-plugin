package com.k.qing.jenkins.plugin.buildline;

import com.k.qing.jenkins.plugin.buildline.bean.BuildLineBuild;
import com.k.qing.jenkins.plugin.buildline.bean.CellBean;
import com.k.qing.jenkins.plugin.buildline.bean.ProjectConfiguration;
import com.k.qing.jenkins.plugin.buildline.bean.TableInfo;
import com.k.qing.jenkins.plugin.buildline.util.*;
import hudson.Extension;
import hudson.model.*;
import hudson.util.RunList;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

/**
 * User: K.Qing
 * Date: 5/6/13
 * Time: 2:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class BuildLineView extends View {

    private String buildViewTitle;
    private List<ProjectConfiguration> lineList;
    private List<TableInfo> tableInfoList;
    
    private static String CI_ARCHIVE_DIR = "ci_archive";

    private static String Jenkins_ARCHIVE_DIR = "archive";
    
    private static String SUMMARY_REPORT_NAME = "summary.txt";

    private String identifier;
    private String firstProjectName;
    private String projects;
    private int maxNum;
    private String summaryFilePath;
    private List<String> projectList = new ArrayList<String>();


    /*@DataBoundConstructor
    public BuildLineView(final String name, final String buildViewTitle, final String initialJobs, List<ProjectConfiguration> lineList, List<TableInfo> tableInfoList) {
        super(name, Hudson.getInstance());
        this.buildViewTitle = buildViewTitle;
        this.lineList = lineList;
        this.tableInfoList = tableInfoList;
    }*/

    @DataBoundConstructor
    public BuildLineView(final String name, final String buildViewTitle, String projects, String identifier) {
        super(name, Hudson.getInstance());
        this.buildViewTitle = buildViewTitle;
        this.identifier = identifier;
        if (projects != null) {
            this.projectList = new ArrayList<String>();
            projectList.addAll(Arrays.asList(projects.split(",")));
        }
    }


    public Map<String, List<CellBean>> getViewData() {
        if (projects != null) {
            this.projectList = new ArrayList<String>();
            this.projectList.addAll(Arrays.asList(projects.split(",")));
        }

        Map<String, List<CellBean>> viewData = new LinkedHashMap<String, List<CellBean>>();
        int maxNum = this.maxNum;
        firstProjectName = projectList.get(0);

        List<String> identifierList = new ArrayList<String>();
        Map<String, AbstractBuild<?, ?>> idBuildMap = new HashMap<String, AbstractBuild<?, ?>>();

        AbstractProject<?, ?> project = (AbstractProject<?, ?>)Jenkins.getInstance().getItem(firstProjectName);
        RunList buildList = project.getBuilds();


        for (int k = 0; k < maxNum; k++) {
            if (k < buildList.size()) {
                AbstractBuild<?, ?> build = (AbstractBuild<?, ?>)buildList.get(k);
                String identifierValue = build.getBuildVariables().get(identifier);

                if(!identifierList.contains(identifierValue)) {
                    identifierList.add(identifierValue);
                    idBuildMap.put(identifierValue, build);
                    List<CellBean> cellBeanList = new ArrayList<CellBean>();
                    CellBean cellBean = new CellBean();
                    cellBean.setContent(getSummaryContent(build, this.summaryFilePath));
                    cellBean.setBuildNumber(build.getNumber());
                    cellBean.setBuild(build);
                    cellBeanList.add(cellBean);
                    viewData.put(identifierValue, cellBeanList);
                }
            } else {
                break;
            }
        }

        for(int j = 0; j < identifierList.size(); j++) {
            for (int i = 1; i < projectList.size(); i++) {
                String projectName = projectList.get(i);
                AbstractBuild<?, ?> build = getBuild(identifierList.get(j), maxNum, projectName);
                if (build != null) {
                    CellBean cellBean = new CellBean();
                    cellBean.setContent(getSummaryContent(build, this.summaryFilePath));
                    cellBean.setBuildNumber(build.getNumber());
                    cellBean.setBuild(build);
                    viewData.get(identifierList.get(j)).add(cellBean);
                }

            }
        }

        return viewData;
    }

    private AbstractBuild<?, ?> getBuild(String id, int maxNum, String projectName) {
        AbstractProject<?, ?> project = (AbstractProject<?, ?>)Jenkins.getInstance().getItem(projectName);
        RunList buildList = project.getBuilds();
        for (int k = 0; k < maxNum; k++) {
            if (k < buildList.size()) {
                AbstractBuild<?, ?> build = (AbstractBuild<?, ?>)buildList.get(k);
                if (build != null) {
                    String identifierValue = build.getBuildVariables().get(identifier);
                    if(identifierValue.equals(id)) {
                        return build;
                    }
                }
            }
        }
        return null;
    }

    private String getSummaryContent(AbstractBuild<?, ?> build, String summaryFilePaths) {

        if (summaryFilePaths != null) {
            String[] summaryFilePathArray = summaryFilePaths.split(",");
            for (String summaryFilePath : summaryFilePathArray) {
                if(summaryFilePath != null) {
                    File summaryFile = new File(build.getRootDir() + "/" + summaryFilePath.trim());
                    if (summaryFile.exists()) {
                        return readFileByLines(summaryFile);
                    }
                }
            }
        }
        return "";
    }

    public static String readFileByLines(File file) {
        StringBuffer sb = new StringBuffer();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String tempString = null;
            while ((tempString = reader.readLine()) != null) {
                sb.append(tempString);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
        return sb.toString();
    }

    /**
     * Get all the information that view page uses.
     * @return
     */
    public Map<TableInfo, List<List<Object>>> getTableData() {
        
        Map<TableInfo, List<List<Object>>> allTableData = new HashMap<TableInfo, List<List<Object>>>();
        
        List<TableInfo> tableInfoList = this.getTableInfoList();
        
        for(TableInfo tableInfo : tableInfoList) {
            List<List<Object>> tableData = new ArrayList<List<Object>>();
            List<String> headerList = tableInfo.getHeaderList();
            Map<String, Map<String, BuildLineBuild>> buildMap = this.getBuildMap(tableInfo);
            Map<String, String> firstJob2TitleValueMap = this.getFirstJob2TitleValue();
            
            List<String> titleList = new ArrayList<String>(firstJob2TitleValueMap.values());
            Collections.sort(titleList);

            for(String title : titleList) {
                Entry<?, ?> entry = this.getEntry(title, buildMap, firstJob2TitleValueMap);
                if(entry != null) {
                    List<Object> row = this.doOperation(entry, headerList, firstJob2TitleValueMap);
                    tableData.add(row);
                }
            }
            allTableData.put(tableInfo,tableData);
        }
        
        return allTableData;
    }
    
    private Map<String, Map<String, BuildLineBuild>> getBuildMap(TableInfo tableInfo) {
        Map<String, Map<String, BuildLineBuild>> buildMap = new HashMap<String,  Map<String, BuildLineBuild>>();

        List<String> initialJobList = this.getInitialJobList(tableInfo);
        for(String initialJob : initialJobList) {
            List<AbstractBuild> buildList = new ArrayList<AbstractBuild>();
            AbstractBuild lastBuild = (AbstractBuild)this.getProject(initialJob).getLastBuild();
            buildList.add(lastBuild);

            if(this.downStreamBuildList != null) {
                this.downStreamBuildList.clear();
            } else {
                this.downStreamBuildList = new ArrayList<AbstractBuild>();
            }

            buildList.addAll(this.getDownStreamBuildList(this.getProject(initialJob).getDownstreamProjects(), lastBuild));

            Map<String, String> lineMap = this.getLineMap(initialJob);//key : jobName, value : header

            Map<String, BuildLineBuild> latestLineMap = new HashMap<String, BuildLineBuild>(); //key :header, value : build

            for (String jobName : lineMap.keySet()) {
                if (hasJob(jobName, buildList)) {
                    AbstractBuild build = this.getBuild(jobName, buildList);
                    if (build != null) {
                    	BuildLineBuild latestBuild = new BuildLineBuild(build);


                        String summary = this.getSummary(build).toString();
                        latestBuild.setSummary(summary);

                        latestLineMap.put(lineMap.get(jobName), latestBuild);
                    }
                } else {
                    latestLineMap.put(lineMap.get(jobName), null);
                }
            }

            buildMap.put(initialJob, latestLineMap);
        }

        return buildMap;
    }
    
    private Map.Entry<?, ?> getEntry(String title, Map<String, Map<String, BuildLineBuild>> buildMap,Map<String, String> firstJob2TitleValueMap) {
        for(Map.Entry entry : buildMap.entrySet()) {
            if(firstJob2TitleValueMap.get(entry.getKey()).equals(title)) {
                return entry;
            }
        }
        return null;
    }
    
    private List<Object> doOperation(Map.Entry entry, List<String> headerList, Map<String, String> firstJob2TitleValueMap) {
        List<Object> row = new ArrayList<Object>();

        for (int i = 0; i < headerList.size(); i++) {
            if (i == 0) {
                row.add(firstJob2TitleValueMap.get(entry.getKey()));
            } else {
                row.add(((Map<String, AbstractBuild>)entry.getValue()).get(headerList.get(i).trim()));
            }
        }

        return row;
    }

    private StringBuffer getSummary(AbstractBuild build) {
        File summary = new File(build.getRootDir() + File.separator + CI_ARCHIVE_DIR, SUMMARY_REPORT_NAME);

        if (!summary.exists()) {
            summary = new File(build.getRootDir() + File.separator + Jenkins_ARCHIVE_DIR, SUMMARY_REPORT_NAME);
        }

        StringBuffer sb = new StringBuffer();

        if (!summary.exists()) {
            return sb;
        }

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(summary));
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line + "\n" + "</br>");
            }

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return sb;
    }

    private boolean hasJob(String jobName, List<AbstractBuild> buildList) {
        for(AbstractBuild build :buildList) {
            String projectName = build.getProject().getName();
            if(jobName.equals(projectName)) {
                return true;
            }
        }
        return false;
    }

    private AbstractBuild getBuild(String jobName, List<AbstractBuild> buildList) {
        for(AbstractBuild build :buildList) {
            String projectName = build.getProject().getName();
            if(jobName.equals(projectName)) {
                return build;
            }
        }
        return null;
    }

    private Map<String, String> getLineMap(String initialJob) {
        Map<String, String> lineMap = new HashMap<String, String>();
        if(initialJob != null) {
            initialJob = initialJob.trim();
        } else {
            return lineMap;
        }

        for(ProjectConfiguration projectConfiguration : this.getLineList()) {

            String projectNames = projectConfiguration.getProjectNames();

            if(projectNames == null || projectNames.isEmpty()) {
                continue;
            }
            String[] items = projectConfiguration.getProjectNames().split(",");

            if(items[1].split(":")[1].trim().equals(initialJob)) {
                for(String item : items) {
                    String header = item.split(":")[0].trim();
                    String jobName = item.split(":")[1].trim();
                    lineMap.put(jobName, header);
                }
            }
        }
        return lineMap;
    }

    private List<AbstractBuild> downStreamBuildList = new ArrayList<AbstractBuild>();
    private List<AbstractBuild> getDownStreamBuildList(List<AbstractProject> downstreamProjectList, AbstractBuild upstreamBuild) {
        if(downstreamProjectList.isEmpty()) {
            return downStreamBuildList;
        } else {
            for(AbstractProject project : downstreamProjectList) {
                List<AbstractProject> nextDownstreamProjectList = project.getDownstreamProjects();
                AbstractBuild build = BuildUtil.getDownstreamBuild(project, upstreamBuild);
                if(build != null) {
                    downStreamBuildList.add(build);
                }

                if(nextDownstreamProjectList.size() != 0) {
                    this.getDownStreamBuildList(nextDownstreamProjectList, build);
                }
            }
        }
        return this.downStreamBuildList;
    }

    private Project getProject(String jobName) {
        List<Project> projectList = Jenkins.getInstance().getProjects();
        for (Project project : projectList) {
            if (jobName.equals(project.getName())) {
                return project;
            }
        }
        return null;
    }

    /**
     * Get first Job name list. The name is unique.
     * @param tableInfo
     * @return
     */
    private List<String> getInitialJobList(TableInfo tableInfo) {
        List<String> initialJobList = new ArrayList<String>();

        List<String> branchList = tableInfo.getBranchList();
        
        for(ProjectConfiguration pc : this.getLineList()) {
            if(branchList.isEmpty()) {
                initialJobList.add(pc.getProjectNames().split(",")[1].split(":")[1].trim());
            } else {
                String branchName = pc.getProjectNames().split(",")[0].split(":")[1].trim();
                if(branchName != null) {
                    if(branchList.contains(branchName)) {
                        initialJobList.add(pc.getProjectNames().split(",")[1].split(":")[1].trim());
                    }
                }
            }
        }

        return initialJobList;
    }

    private Map<String, String> getFirstJob2TitleValue() {
        Map<String, String> firstJob2TitleValueMap = new HashMap<String, String>();

        for(ProjectConfiguration pc : this.getLineList()) {
            firstJob2TitleValueMap.put(pc.getProjectNames().split(",")[1].split(":")[1].trim(), pc.getProjectNames().split(",")[0].split(":")[1].trim());
        }

        return firstJob2TitleValueMap;
    }
    

    @Override
    public Collection<TopLevelItem> getItems() {
        return Hudson.getInstance().getItems();
    }

    @Override
    public boolean contains(TopLevelItem item) {
        return this.getItems().contains(item);
    }

    @Override
    public void onJobRenamed(Item item, String oldName, String newName) {
        System.out.println("This is my on job renamed.");
    }

    @Override
    protected void submit(StaplerRequest req) throws IOException, ServletException, Descriptor.FormException {
        this.buildViewTitle = req.getParameter("buildViewTitle");
        this.identifier = req.getParameter("identifier");
        this.projects = req.getParameter("projects");
        this.summaryFilePath = req.getParameter("summaryFilePath");
        String maxNumStr = req.getParameter("maxNum");
        if (maxNumStr != null) {
            try {
                this.maxNum = Integer.parseInt(maxNumStr);
            } catch (Exception e) {
                e.printStackTrace();
                this.maxNum = 10;
            }
        } else {
            this.maxNum = 10;
        }
//        String[] projectNames = req.getParameterValues("projectNames");
//
//        this.tableInfoList = req.bindParametersToList(TableInfo.class, "table_");
//
//        List<ProjectConfiguration> projectConfigurationList = new ArrayList<ProjectConfiguration>();
//        for(String projectName : projectNames) {
//            ProjectConfiguration projectConfiguration = new ProjectConfiguration(projectName);
//            projectConfigurationList.add(projectConfiguration);
//        }
//
//        this.lineList = projectConfigurationList;

    }

    @Override
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getBuildViewTitle() {
        return buildViewTitle;
    }

    public void setBuildViewTitle(final String buildViewTitle) {
        this.buildViewTitle = buildViewTitle;
    }

    public List<ProjectConfiguration> getLineList() {
        return lineList;
    }

    public void setLineList(List<ProjectConfiguration> lineList) {
        this.lineList = lineList;
    }

    private void addLineList(ProjectConfiguration projectConfiguration) {
        this.lineList.add(projectConfiguration);
    }

    public List<TableInfo> getTableInfoList() {
        return tableInfoList;
    }

    public void setTableInfoList(List<TableInfo> tableInfoList) {
        this.tableInfoList = tableInfoList;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public List<String> getProjectList() {
        return projectList;
    }

    public void setProjectList(List<String> projectList) {
        this.projectList = projectList;
    }

    public String getProjects() {
        return projects;
    }

    public void setProjects(String projects) {
        this.projects = projects;
    }

    public int getMaxNum() {
        return maxNum;
    }

    public void setMaxNum(int maxNum) {
        this.maxNum = maxNum;
    }

    public String getSummaryFilePath() {
        return summaryFilePath;
    }

    public void setSummaryFilePath(String summaryFilePath) {
        this.summaryFilePath = summaryFilePath;
    }

    /**
     * This descriptor class is required to configure the View Page
     *
     */
    @Extension
    public static final class DescriptorImpl extends ViewDescriptor {
        
        public DescriptorImpl() {
            super();
        }

        @Override
        public String getDisplayName() {
            return "Latest Build View";
        }
    }



}
