package com.godaddy.sonar.ruby.metricfu;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.PersistenceMode;
import org.sonar.api.resources.Project;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.scan.filesystem.FileQuery;
import org.sonar.api.scan.filesystem.ModuleFileSystem;
import org.sonar.api.measures.RangeDistributionBuilder;

import com.godaddy.sonar.ruby.core.Ruby;
import com.godaddy.sonar.ruby.core.RubyFile;

public class MetricfuComplexitySensor implements Sensor
{
    private static final Logger LOG = LoggerFactory.getLogger(MetricfuComplexitySensor.class);

    private MetricfuComplexityYamlParser metricfuComplexityYamlParser;
    private ModuleFileSystem moduleFileSystem;
    private static final Number[] FILES_DISTRIB_BOTTOM_LIMITS = { 0, 5, 10, 20, 30, 60, 90 };
    private static final Number[] FUNCTIONS_DISTRIB_BOTTOM_LIMITS = { 1, 2, 4, 6, 8, 10, 12, 20, 30 };
    private final ResourcePerspectives perspectives;

    public MetricfuComplexitySensor(ModuleFileSystem moduleFileSystem, MetricfuComplexityYamlParser metricfuComplexityYamlParser, ResourcePerspectives p)
    {
        this.moduleFileSystem = moduleFileSystem;
        this.metricfuComplexityYamlParser = metricfuComplexityYamlParser;
        this.perspectives = p;
    }

    public boolean shouldExecuteOnProject(Project project)
    {
        return Ruby.KEY.equals(project.getLanguageKey());
    }

    public void analyse(Project project, SensorContext context)
    {
        File resultsFile = new File("tmp/metric_fu/report.yml");
        List<File> sourceDirs = moduleFileSystem.sourceDirs();
        List<File> rubyFilesInProject = moduleFileSystem.files(FileQuery.onSource().onLanguage(project.getLanguageKey()));

        for (File file : rubyFilesInProject)
        {
            LOG.debug("analyzing functions for classes in the file: " + file.getName());
            try
            {
                analyzeFile(file, sourceDirs, context, resultsFile);
            } catch (IOException e)
            {
                LOG.error("Can not analyze the file " + file.getAbsolutePath() + " for complexity");
            }
        }
    }

    private void analyzeFile(File file, List<File> sourceDirs, SensorContext sensorContext, File resultsFile) throws IOException
    {
        RubyFile resource = new RubyFile(file, sourceDirs);
        List<RubyFunction> functions = metricfuComplexityYamlParser.parseFunctions(resource.getName(), resultsFile);

        // if function list is empty, then return, do not compute any complexity
        // on that file
        if (functions.isEmpty())
        {
            return;
        }

        // COMPLEXITY
        int fileComplexity = 0;
        for (RubyFunction function : functions)
        {
            fileComplexity += function.getComplexity();
        }
        sensorContext.saveMeasure(resource, CoreMetrics.COMPLEXITY, Double.valueOf(fileComplexity));

        // FILE_COMPLEXITY_DISTRIBUTION
        RangeDistributionBuilder fileDistribution = new RangeDistributionBuilder(CoreMetrics.FILE_COMPLEXITY_DISTRIBUTION, FILES_DISTRIB_BOTTOM_LIMITS);
        fileDistribution.add(Double.valueOf(fileComplexity));
        sensorContext.saveMeasure(resource, fileDistribution.build().setPersistenceMode(PersistenceMode.MEMORY));

        // FUNCTION_COMPLEXITY
        sensorContext.saveMeasure(resource, CoreMetrics.FUNCTION_COMPLEXITY, Double.valueOf(fileComplexity) / functions.size());

        // FUNCTION_COMPLEXITY_DISTRIBUTION
        RangeDistributionBuilder functionDistribution = new RangeDistributionBuilder(CoreMetrics.FUNCTION_COMPLEXITY_DISTRIBUTION, FUNCTIONS_DISTRIB_BOTTOM_LIMITS);
        for (RubyFunction function : functions)
        {
            functionDistribution.add(Double.valueOf(function.getComplexity()));
        }
        sensorContext.saveMeasure(resource, functionDistribution.build().setPersistenceMode(PersistenceMode.MEMORY));
    }
    
    @SuppressWarnings("unchecked")
    private boolean addIssue(SensorContext context, RubyFile myResource, String ruleRepo, String ruleKey, String messageDescription, String severity) {   
		
		boolean flag = false;
		Issuable issuable = null;
		Issue issue = null;
		
		//Create issuable object.
    	try{
    		issuable = perspectives.as(Issuable.class, myResource);
    	}catch(Exception e){
    		LOG.error("Can not create object of Issuable " + e.getMessage());
    	}
		
    	//Create new issue.
		try{
			issue = issuable.newIssueBuilder()
		               .ruleKey(RuleKey.of(ruleRepo, ruleKey))
		               .message(messageDescription)
		               .severity(severity)
		               .build();
			LOG.info("Issue Object : " + issue.toString());
		}catch(Exception e){
			LOG.error("Error in create issu object" + e.getMessage());	
		}
		
		//Register new Issue.
		try{
			flag = issuable.addIssue(issue);
			LOG.info("Issue is registered in the context successfully.");
		}catch(Exception e){
			LOG.error("Error in create issue object" + e.getMessage());
		}
		
		//Return.
		return flag;
	}
}
