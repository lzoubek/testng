package org.testng.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.testng.ClassMethodMap;
import org.testng.ITestClass;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.TestRunner;
import org.testng.internal.thread.ThreadUtil;
import org.testng.xml.XmlSuite;

/**
 * This class implements Runnable and will invoke the ITestMethod passed in its
 * constructor on its run() method.
 * 
 * @author <a href="mailto:cedric@beust.com">Cedric Beust</a>
 */
public class TestMethodWorker implements Runnable {
  private ITestNGMethod[] m_testMethods;
  private IInvoker m_invoker = null;
  private Map<String, String> m_parameters = null;
  private XmlSuite m_suite = null;
  private Map<ITestClass, ITestClass> m_invokedBeforeClassMethods = null;
  private Map<ITestClass, ITestClass> m_invokedAfterClassMethods = null;
  private ITestNGMethod[] m_allTestMethods;
  private List<ITestResult> m_testResults = new ArrayList<ITestResult>();
  private ConfigurationGroupMethods m_groupMethods = null;
  private ClassMethodMap m_classMethodMap = null;
  
  public TestMethodWorker(IInvoker invoker, 
                          ITestNGMethod[] testMethods,
                          XmlSuite suite,
                          Map<String, String> parameters,
                          Map<ITestClass, ITestClass> invokedBeforeClassMethods,
                          Map<ITestClass, ITestClass> invokedAfterClassMethods,
                          ITestNGMethod[] allTestMethods,
                          ConfigurationGroupMethods groupMethods,
                          ClassMethodMap classMethodMap)
  {
    m_invoker = invoker;
    m_testMethods = testMethods;
    m_suite = suite;
    m_parameters = parameters;
    m_invokedBeforeClassMethods = invokedBeforeClassMethods;
    m_invokedAfterClassMethods = invokedAfterClassMethods;
    m_allTestMethods = allTestMethods;
    m_groupMethods = groupMethods;
    m_classMethodMap = classMethodMap;
  }
  
  /**
   * Retrieves the maximum specified timeout of all ITestNGMethods to
   * be run.
   * 
   * @return the max timeout or 0 if no timeout was specified
   */
  public long getMaxTimeOut() {
    long result = 0;
    for (ITestNGMethod tm : m_testMethods) {
      if (tm.getTimeOut() > result) {
        result = tm.getTimeOut();
      }
    }
    
    return result;
  }
  
  @Override
  public String toString() {
    return "[Worker on thread:" + Thread.currentThread().getId() + " " + m_testMethods[0] + "]";
  }
  
  /**
   * Run all the ITestNGMethods passed in through the constructor.
   * 
   * @see java.lang.Runnable#run()
   */
  public void run() {
    // Using an index here because we need to tell the invoker
    // the index of the current method
    for (int indexMethod = 0; indexMethod < m_testMethods.length; indexMethod++) {
      ITestNGMethod tm = m_testMethods[indexMethod];
  
      //
      // Invoke the before class methods if not done already
      //
      ITestClass testClass = tm.getTestClass();

      // the whole invocation must be synchronized as other threads must
      // get a full initialized test object (not the same for @After)
      synchronized(m_invokedBeforeClassMethods) {
        if (! m_invokedBeforeClassMethods.containsKey(testClass)) {  
          m_invokedBeforeClassMethods.put(testClass, testClass);
          m_invoker.invokeConfigurations(testClass,
              testClass.getBeforeClassMethods(),
              m_suite,
              m_parameters,
              null /* instance */);
        }
      }

      //
      // Invoke test method
      //
      
      // Potential bug here:  we look up the method index of tm among all
      // the test methods (not very efficient) but if this method appears
      // several times and these methods are run in parallel, the results
      // are unpredictable...  Need to think about this more (and make it
      // more efficient)
      List<ITestResult> testResults = m_invoker.invokeTestMethods(tm, 
          m_suite, 
          m_parameters, 
          m_allTestMethods, 
          indexOf(tm, m_allTestMethods), 
          m_groupMethods);
      
      if (testResults != null) {
        m_testResults.addAll(testResults);        
      }
      
      //
      // Invoke after class methods if this test method is the last one
      // on this class
      //
      if (m_classMethodMap.removeAndCheckIfLast(tm)) {
        boolean invokeAfter= false;
        synchronized(m_invokedAfterClassMethods) {
          if (! m_invokedAfterClassMethods.containsKey(testClass)) {
            m_invokedAfterClassMethods.put(testClass, testClass);
            invokeAfter= true;
          }
        }
        
        if(invokeAfter) {
          m_invoker.invokeConfigurations(testClass,
              testClass.getAfterClassMethods(),
              m_suite,
              m_parameters,
              null /* instance */);
        }
      }
    }
  }
  
  private int indexOf(ITestNGMethod tm, ITestNGMethod[] allTestMethods) {
    for (int i = 0; i < allTestMethods.length; i++) {
      if (allTestMethods[i] == tm) return i;
    }
    return -1;
  }

  public List<ITestResult> getTestResults() {
    return m_testResults;
  }
  
  private boolean isLastTestMethodForClass(ITestNGMethod tm, 
      ITestNGMethod[] testMethods)
  {
    for (int i = testMethods.length - 1; i >= 0; i--) {
      ITestNGMethod thisMethod = testMethods[i];
      ITestClass testClass = tm.getTestClass();
      if (thisMethod.getTestClass().equals(testClass)) {
        if (thisMethod.equals(tm)) {
          return true;
        }
        else {
          return false;
        }
      }
    }
    
    return false;
  }

  private void ppp(String s) {
    if (TestRunner.getVerbose() >= 2) {
      System.out.println("[TestMethodWorker " + ThreadUtil.currentThreadInfo() + "] " + s);
    }
  }

  public void setAllTestMethods(ITestNGMethod[] allTestMethods) {
    m_allTestMethods = allTestMethods;
  }
}