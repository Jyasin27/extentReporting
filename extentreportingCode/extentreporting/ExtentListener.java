package extentreporting;

import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Method;

public class ExtentListener implements TestWatcher, AfterEachCallback
{
    private static ExtentReporter extent = null;
    private static final String extentName = "EXTENTREPORTER";

    public static void setExtent(ExtentReporter extent)
    {
        ExtentListener.extent = extent;
    }

    private ExtentReporter getExtentFromStore(ExtensionContext context)
    {
        return ((ExtentReporter)getStore(context).get(extentName));
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        getStore(context).put(extentName, extent);
    }
    //Stores all test information
    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass(), context.getClass()));
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        Method testMethod = context.getRequiredTestMethod(); //Prints out test method used

        try {
            System.out.println((testMethod.getName() +  "Pass"));
            getExtentFromStore(context).stepPassed("Test Complete");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause)
    {
        try
        {
            getExtentFromStore(context).stepFailed("Test Fail [Name] - " + context.getUniqueId());
            getExtentFromStore(context).stepFailed("Test Fail [Cause] - " + cause.toString());
        } catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }
}
