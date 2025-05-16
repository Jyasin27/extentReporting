package extentreporting;

import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import selenium.SeleniumSupport;
import com.aventstack.extentreports.*;
import com.aventstack.extentreports.markuputils.CodeLanguage;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.awt.*;
import java.io.File;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class ExtentReporter //extends SeleniumSupport
{
    private ExtentReports report;
    private List<ExtentTest> extentTests = new ArrayList<>();
    private String unitTestName;
    public boolean isTestParallel = false;
    public boolean isReportingTurnedOff = false;
    private int screenshotCounter = 0;
    private WebDriver driver = null;

    public ExtentReporter(WebDriver driver, TestInfo testInfo)
    {
        this.driver = driver;
        setUnitTestName(testInfo.getDisplayName().replaceAll("[()]", ""));
        setup();
    }

    public ExtentReporter(WebDriver driver, String unitTestName)
    {
        this.driver = driver;
        setUnitTestName(unitTestName.replaceAll("[()]", ""));
    }

    public String getUnitTestName()
    {
        return unitTestName;
    }

    //This name must be set at runtime
    public void setUnitTestName(String unitTestName)
    {
        this.unitTestName = unitTestName;
    }

    private static String _reportDirectory;

    public static String getReportDirectory()
    {
        return _reportDirectory;
    }

    public static void setReportDirectory(String dir)
    {
        _reportDirectory = dir;
    }

    private void setOutputStream()
    {
        try
        {
            PrintStream fileStream = new PrintStream(getReportDirectory() + "Output.txt");
            System.setOut(fileStream);
        }
        catch (Exception e)
        {

        }
    }

    /**
     * Setup() uses the getUnitTestName() getter to create the report directory and initialize extent report objects.
     */
    public void setup()
    {

        setReportDirectory(System.getProperty("user.dir") + "\\Reports\\" + getUnitTestName() + "\\" + getCurTime() + "\\");
        new File(getReportDirectory()).mkdirs();

        if(isReportingTurnedOff)
        {
            setOutputStream();
            return;
        }

        report = new ExtentReports();
        ExtentSparkReporter html = new ExtentSparkReporter(getReportDirectory() + "ExtentReport.html");
        //https://stackoverflow.com/questions/53790432/how-to-enhance-the-image-size-of-the-screenshot-captured-for-extent-report
        String css = ".r-img {width: 50%;}";
        html.config().setCSS(css);

        report.attachReporter(html);
        report.setAnalysisStrategy(AnalysisStrategy.TEST);
        report.flush();
    }

    /**
     * CreateTest() uses the initialized Report Objects to add a new test into it.
     * <p>
     * Note on getCurrentTestName() for Parallel Execution:
     * This method does not call the getCurrentTestName() method.
     * This is due to that method's limitation of only knowing it's test name while running inside an @TEST method tag.
     * If this createTest was called within a @BeforeAll or @BeforeEvery, the test name received at that time from this method 'getCurrentTestName()' would be null.
     * Therefore, in order to use CreateTest(), you must first set the unitTestName variable.
     */
    public ExtentTest createTest()
    {
        if(isReportingTurnedOff)
            return null;

        try
        {
            if (report == null) setup();
            ExtentTest test = report.createTest(getUnitTestName());
            System.out.println("[SUCCESS} Created test: " + getUnitTestName());

            extentTests.add(test);
            return test;
        } catch (Exception e)
        {
            printError("Error thrown while trying to create new ExtentTest", e);
            throw e;
        }
    }

    /**
     * public Method for defining unitTestName when creating a new test.
     */
    public ExtentTest createTest(String unitTestName)
    {
        if(isReportingTurnedOff)
            return null;

        try
        {
            setUnitTestName(unitTestName);
            if (report == null) setup();
            ExtentTest test = report.createTest(unitTestName);
            System.out.println("[SUCCESS} Created test: " + getUnitTestName());

            extentTests.add(test);
            return test;
        } catch (Exception e)
        {
            printError("Error thrown while trying to create new ExtentTest", e);
            throw e;
        }
    }


    /**
     * List<ExtentTest> testList was added to facilitate reporting with parallel execution.
     * Using Lambda, we find the correct test that matches the testName provided.
     * If there's no match, we'll create the test and return that.
     */
    public ExtentTest getTest(String testName)
    {
        if(isReportingTurnedOff)
            return null;

        if (report == null) setup();
        if (extentTests.size() < 1)
        {
            return createTest(testName);
        }

        List<ExtentTest> testList = extentTests.stream().filter(s -> s.getModel().getName().equals(testName)).collect(Collectors.toList());

        if (testList != null && !testList.isEmpty() && testList.size() > 0)
        {
            ExtentTest test = testList.get(0);
            return (test != null) ? test : createTest(testName);
        } else
        {
            return createTest(testName);
        }
    }

    /**
     * This method dynamically retrieves the name of the current test running.
     * It only works when an @Test method is running. If called in the @Before or @After tags, it will fail to return a name.
     * In those instances, the getUnitTestName() method which is always set in the @BeforeAll, will be defaulted to.
     * This method is necessary for Parallel Execution.
     * https://stackoverflow.com/questions/473401/get-name-of-currently-executing-test-in-junit-4 | https://stackoverflow.com/a/4566953
     */
    public String getCurrentTestName()
    {
        if(isReportingTurnedOff)
            return null;

        if (!isTestParallel)
        {
            return getUnitTestName();
        } else
        {

            pause(150);//added to assist with Parallel Execution > ConcurrentModificationException
            String testName = null;
            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
            for (int i = trace.length - 1; i > 0; --i)
            {
                StackTraceElement ste = trace[i];
                try
                {
                    Class<?> cls = Class.forName(ste.getClassName());
                    Method method = cls.getDeclaredMethod(ste.getMethodName());
                    Test annotation = method.getAnnotation(Test.class);
                    if (annotation != null)
                    {
//                    testName = ste.getClassName() + "." + ste.getMethodName();
                        testName = ste.getMethodName();
                        break;
                    }
                    //The below exceptions are expected to be thrown while cycling through these objects.
                } catch (ClassNotFoundException e)
                {
                } catch (NoSuchMethodException e)
                {
                } catch (SecurityException e)
                {
                }
            }
            if (testName != null && !testName.isEmpty())
            {
                return testName;
            } else
            {//If all else fails, return the UnitTestClassName provided in the @BeforeALL.
                // This will be required for @TestFactory unit tests. Commenting out println until it is required.
//            System.out.println("[REPORTING] Unable to perform Reporting.getCurrentTestName() > Defaulting to getUnitTestName(): " + getUnitTestName());
                return getUnitTestName();
            }
        }
    }

    /**
     * Pass message added to the current test in the report.
     */
    public void stepPassed(String message)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[SUCCESS] - " + message);
            return;
        }

        ExtentTest test = getTest(getCurrentTestName());
        test.pass(message);
        System.out.println("[SUCCESS] - " + message);
        report.flush();
    }

    /**
     * Info message added to the current test in the report.
     */
    public void stepInfo(String message)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[INFO] - " + message);
            return;
        }
        ExtentTest test = getTest(getCurrentTestName());
        test.info(message);
        System.out.println("[INFO] - " + message);
        report.flush();
    }

    /**
     * Warning message added to the current test in the report.
     */
    public void stepWarning(String message)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[WARN] - " + message);
            return;
        }

        ExtentTest test = getTest(getCurrentTestName());
        test.warning(message);
        System.out.println("[WARN] - " + message);
        report.flush();
    }

    /**
     * Failed message added to the current test in the report.
     */
    public void stepFailed(String message)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[FAIL] - " + message);
            return;
        }

        ExtentTest test = getTest(getCurrentTestName());
        test.fail(message);
        System.out.println("[FAIL] - " + message);
        report.flush();
    }

    /**
     * Fatal message added to the current test in the report.
     */
    public void stepFatal(String message)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[FATAL] - " + message);
            return;
        }

        ExtentTest test = getTest(getCurrentTestName());
        test.fatal(message);
        System.out.println("[FATAL] - " + message);
        report.flush();
    }

    /**
     * Pass message added to the current test in the report.
     * As well as an xml code block that get formatted.
     */
    public void stepPassedWithXML(String message, String xmlCodeBlock)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[SUCCESS] - " + message);
            System.out.println(xmlCodeBlock);
            return;
        }

        ExtentTest test = getTest(getCurrentTestName());
        test.pass(message);
        test.pass(MarkupHelper.createCodeBlock(getPrettyFormatXML(xmlCodeBlock, 2), CodeLanguage.XML));
        System.out.println("[SUCCESS] - " + message);
        report.flush();
    }

    /**
     * Pass message added to the current test in the report.
     * As well as a JSON code block that get formatted.
     */
    public void stepPassedWithJSON(String message, String jsonCodeBlock)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[SUCCESS] - " + message);
            System.out.println(jsonCodeBlock);
            return;
        }

        ExtentTest test = getTest(getCurrentTestName());
        test.pass(message);
        test.pass(MarkupHelper.createCodeBlock(getPrettyFormatJSON(jsonCodeBlock), CodeLanguage.JSON));
        System.out.println("[SUCCESS] - " + message);
        report.flush();
    }

    /**
     * Fail message added to the current test in the report.
     * As well as an xml code block that get formatted.
     */
    public void stepFailedWithXML(String message, String xmlCodeBlock)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[FAIL] - " + message);
            System.out.println(xmlCodeBlock);
            return;
        }

        ExtentTest test = getTest(getCurrentTestName());
        test.fail(message);
        test.fail(MarkupHelper.createCodeBlock(getPrettyFormatXML(xmlCodeBlock, 2), CodeLanguage.XML));
        System.out.println("[FAIL] - " + message);
        report.flush();
    }

    /**
     * Fail message added to the current test in the report.
     * As well as a JSON code block that get formatted.
     */
    public void stepFailedWithJSON(String message, String jsonCodeBlock)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[FAIL] - " + message);
            System.out.println(jsonCodeBlock);
            return;
        }

        ExtentTest test = getTest(getCurrentTestName());
        test.fail(message);
        test.fail(MarkupHelper.createCodeBlock(getPrettyFormatJSON(jsonCodeBlock), CodeLanguage.JSON));
        System.out.println("[FAIL] - " + message);
        report.flush();
    }

    /**
     * Pass message added to the current test in the report.
     * As well as a Table block that get formatted.
     * This method uses a varargs signature to allow for headers to be added as required.
     */
    public void stepPassedWithTable(String message, List<ArrayList<String>> tableList, String... headers)
    {//http://extentreports.com/docs/versions/4/java/
        /*
        Example of populating a List of ArrayList
        List<ArrayList<String>> tableList = new ArrayList<>();
        ListOfStringDataWithValues.forEach(s -> tableList.add(new ArrayList<String>(Arrays.asList(s.getKey(), s.getValue()))));
         */


        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[SUCCESS] - " + message);
            for(int i=0; i<headers.length; i++)
            {
                System.out.println(headers[i]);
                tableList.get(i).stream().forEach(x -> System.out.println(x));
            }
            return;
        }

        try
        {
            //Adding Headers into tableList
            ArrayList headerList = new ArrayList();
            for (String header : headers)
            {
                headerList.add(header);
            }
            tableList.add(0, headerList);

            //Creating 2D Array table
            String[][] tableArray = convertListToTable(tableList);
            if (tableArray == null) throw new NullPointerException("Failed to convert List to Table");

            ExtentTest test = getTest(getCurrentTestName());
            test.pass(message);
            test.pass(MarkupHelper.createTable(tableArray));
            System.out.println("[SUCCESS] - " + message);
            report.flush();
        } catch (Exception e)
        {
            stepFailed("Failed to produce Table in extent Report - " + e.getMessage());
            throw e;
        }
    }

    /**
     * Pass message added to the current test in the report.
     * As well as a Table block that get formatted.
     * This method uses a List for headers to be added.
     */
    public void stepPassedWithTable(String message, List<ArrayList<String>> tableList, ArrayList<String> headerList)
    {//http://extentreports.com/docs/versions/4/java/
        /*
        Example of populating a List of ArrayList
        List<ArrayList<String>> tableList = new ArrayList<>();
        ListOfStringDataWithValues.forEach(s -> tableList.add(new ArrayList<String>(Arrays.asList(s.getKey(), s.getValue()))));
         */


        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[SUCCESS] - " + message);
            for(int i=0; i<headerList.size(); i++)
            {
                System.out.println(headerList.get(i));
                tableList.get(i).stream().forEach(x -> System.out.println(x));
            }
            return;
        }

        try
        {
            //Adding Headers into tableList
            tableList.add(0, headerList);

            //Creating 2D Array table
            String[][] tableArray = convertListToTable(tableList);
            if (tableArray == null) throw new NullPointerException("Failed to convert List to Table");

            ExtentTest test = getTest(getCurrentTestName());
            test.pass(message);
            test.pass(MarkupHelper.createTable(tableArray));
            System.out.println("[SUCCESS] - " + message);
            report.flush();
        } catch (Exception e)
        {
            stepFailed("Failed to produce Table in extent Report - " + e.getMessage());
            throw e;
        }
    }

    /**
     * This method is created to allow us to convert a List of ArrayList into a 2D String Array.
     * This is necessary due to Extent Reports only supporting 2D arrays for the table markup.
     */
    private String[][] convertListToTable(List<ArrayList<String>> list)
    {//https://stackoverflow.com/questions/10043209/convert-arraylist-into-2d-array-containing-varying-lengths-of-arrays
        try
        {
            String[][] array = new String[list.size()][];
            for (int i = 0; i < list.size(); i++)
            {
                ArrayList<String> row = list.get(i);
                array[i] = row.toArray(new String[row.size()]);
            }
            return array;
        } catch (Exception e)
        {
            stepFailed("Failed to convert List to table - " + e.getMessage());
            throw e;
        }
    }

    /**
     * Pass message added to the current test in the report with a color label that you want.
     */
    public void stepPassedWithLabel(String label, ExtentColor color)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[SUCCESS] - " + label);
            return;
        }

        ExtentTest test = getTest(getCurrentTestName());
        test.pass(MarkupHelper.createLabel(label, color));
        System.out.println("[SUCCESS] - " + label);

        report.flush();
    }

    /**
     * Pass message added to the current test in the report with screenshot added in-line.
     * SeleniumDriver provides the screenshot relative path used.
     */
    public void stepPassedWithScreenshot(String message)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[SUCCESS] - " + message);
            return;
        }

        String screenshotRelativePath = takeScreenshot(true);
        ExtentTest test = getTest(getCurrentTestName());
        try
        {
            test.pass(message, MediaEntityBuilder.createScreenCaptureFromPath(screenshotRelativePath).build());

        } catch (Exception e)
        {//Screenshots may fail when automating IE. Does not mean there is a test failure.
            printError("Failed Capturing Screenshot for message '" + message + "'", e);
            test.pass(message + " - screenshot capture failure");
        }

        System.out.println(message);
        report.flush();
    }

    /**
     * Failed message added to the current test in the report with screenshot added in-line.
     * SeleniumDriver provides the screenshot relative path used.
     */
    public void stepFailedWithScreenshot(String message)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[Failed] - " + message);
            return;
        }
        String screenshotRelativePath = takeScreenshot(false);

        ExtentTest test = getTest(getCurrentTestName());
        try
        {
            test.fail(message, MediaEntityBuilder.createScreenCaptureFromPath(screenshotRelativePath).build());

        } catch (Exception e)
        {
            printError("Failed Capturing Screenshot for message '" + message + "'", e);
            test.fail(message + " - screenshot capture failure");
        }

        System.out.println(message);
        report.flush();
    }

    /**
     * Warning message added to the current test in the report with screenshot added in-line.
     * SeleniumDriver provides the screenshot relative path used.
     */
    public void stepWarningWithScreenshot(String message)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[WARN] - " + message);
            return;
        }

        String screenshotRelativePath = takeScreenshot(true);
        ExtentTest test = getTest(getCurrentTestName());
        try
        {
            test.warning(message, MediaEntityBuilder.createScreenCaptureFromPath(screenshotRelativePath).build());

        } catch (Exception e)
        {
            printError("Failed Capturing Screenshot for message '" + message + "'", e);
            test.warning(message + " - screenshot capture failure");
        }

        System.out.println(message);
        report.flush();
    }

    /**
     * Skip message added to the current test in the report with screenshot added in-line.
     * SeleniumDriver provides the screenshot relative path used.
     */
    public void stepSkipWithScreenshot(String message)
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "[SKIP] - " + message);
            return;
        }
        String screenshotRelativePath = takeScreenshot(true);

        ExtentTest test = getTest(getCurrentTestName());
        try
        {
            test.skip(message, MediaEntityBuilder.createScreenCaptureFromPath(screenshotRelativePath).build());

        } catch (Exception e)
        {
            printError("Failed Capturing Screenshot for message '" + message + "'", e);
            test.skip(message + " - screenshot capture failure");
        }

        System.out.println(message);
        report.flush();
    }

    /**
     * TestComplete message added at the end of the current test in the report.
     */
    public void finaliseTest()
    {
        if(isReportingTurnedOff)
        {
            System.out.println(getCurDateTime() + "Test Complete");
            return;
        }

        ExtentTest test = getTest(getUnitTestName());
        test.pass("Test Complete!");
        System.out.println("[COMPLETE] - Test Complete");
        report.flush();
    }

    /**
     * getCurTime is used to create unique folder name for reporting structure.
     */
    private String getCurTime()
    {
        Date date = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("dd-MM-yyyy hh-mm-ss");

        return ft.format(date);
    }
    /**
     * getCurTime is used to create unique folder name for reporting structure.
     */
    private String getCurDateTime()
    {
        Date date = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("dd-MM-yyyy hh-mm-ss");

        return "[" + ft.format(date) + "] ";
    }

    /**
     * openReport allows us to automatically report the report on testcompletion.
     */
    public void openReport(boolean isOpenTrue)
    {
        String reportPath = getReportDirectory() + "ExtentReport.html";
        if(isReportingTurnedOff)
        {
            reportPath = getReportDirectory() + "Output.txt";
        }

        System.out.println("Report Path: \n" + reportPath + "\n");

        if (!isOpenTrue)
        {
            return;
        }

        try
        {
            Desktop desktop = null;
            if (Desktop.isDesktopSupported())
            {
                desktop = Desktop.getDesktop();
            }

            desktop.open(new File(reportPath));
        } catch (Exception ioe)
        {
            ioe.printStackTrace();
        }
    }

    /**
     * Takes ugly xml and pretty formats it for the report.
     */
    public String getPrettyFormatXML(String input, int indent)
    {//https://stackoverflow.com/questions/139076/how-to-pretty-print-xml-from-java
        try
        {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", indent);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
        } catch (Exception e)
        {
            System.out.println("Failed to make xml input pretty");
            return input;
        }
    }

    /**
     * Takes ugly JSON and pretty formats it for the report.
     */
    public String getPrettyFormatJSON(String input)
    {//https://stackoverflow.com/questions/4105795/pretty-print-json-in-java
        try
        {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonParser jp = new JsonParser();
            JsonElement je = jp.parse(input);
            return gson.toJson(je);
        } catch (Exception e)
        {
            System.out.println("Failed to make JSON input pretty");
            return input;
        }
    }

    /**
     * error print method.
     */
    private void printError(String msg)
    {
        System.err.println(msg);
    }

    /**
     * error print method with exception.
     */
    private void printError(String msg, Exception e)
    {
        System.err.println(msg + " - " + e.getMessage());
    }

    /**
     * Manual Pause for a hard wait when it is required.
     */
    public void pause(int millis)
    {
        try
        {
            Thread.sleep(millis);
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * This method is called to capture Screenshots with Selenium.
     * This method returns a relative path to the captured screenshot.
     * We then use that path and add it to the report.
     */
    public String takeScreenshot(boolean isPass)
    {
        //Counter must increment for unique image names as well as image order.
        screenshotCounter++;
        StringBuilder imagePathBuilder = new StringBuilder();
        StringBuilder relativePathBuilder = new StringBuilder();
        try
        {
            //getReportDirectory() is a method from the baseclass that is set in the Reporting Class.
            imagePathBuilder.append(ExtentReporter.getReportDirectory());
            relativePathBuilder.append("Screenshots\\");
            //We create a new folder for Screenshots within the report folder.
            new File(imagePathBuilder.toString() + (relativePathBuilder).toString()).mkdirs();

            //Building out the screenshot naming notation. e.g. 1_PASSED.png, 2_FAILED.png
            relativePathBuilder.append(screenshotCounter + "_");
            String passFail = (isPass) ? "PASSED" :  "FAILED";
            relativePathBuilder.append(passFail);
            relativePathBuilder.append(".png");

            //The actual screenshot capturing using the selenium driver.
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(screenshot, new File(imagePathBuilder.append(relativePathBuilder).toString()));

            return "./" + relativePathBuilder.toString();
        } catch (Exception e)
        {
            //Watch this message. It might cause extent failures for silly driver issues. Especially when automating IE.
            stepFailed("Failed to capture screenshot and return relative path - " + e.getMessage());
            return null;
        }

    }
}
