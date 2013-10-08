package com.godaddy.sonar.ruby.metricfu;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.sonar.api.BatchExtension;

public interface MetricfuComplexityYamlParser extends BatchExtension
{
    List<RubyFunction> parseFunctions(String fileName, File resultsFile) throws IOException;
    
    List<ArrayList<String>> parseFileForIssues(File resultsFile) throws IOException;
}
