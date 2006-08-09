package test.methodselectors;

import java.util.List;

import org.testng.IMethodSelector;
import org.testng.ITestNGMethod;

public class AllTestsMethodSelector implements IMethodSelector {

  public boolean includeMethod(ITestNGMethod method, boolean isTestMethod) {
    return true;
  }
  
  private static void ppp(String s) {
    System.out.println("[MyMethodSelector] " + s);
  }

  public void setTestMethods(List<ITestNGMethod> testMethods) {
    // TODO Auto-generated method stub
    
  }

}