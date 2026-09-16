package io.github.mawsonlakes790913.chineseoutputforge.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@Slf4j
public class LogAspect {
	
    // 対象: Controller
    @Pointcut("@within(org.springframework.stereotype.Controller)")
    public void controllerMethods() {}

    // 対象: Service
    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void serviceMethods() {}
	
	// 実行前にログ出力する(Controller)
    @Before("controllerMethods()")
    public void startControllerLog(JoinPoint jp) {
        log.debug("Controller開始: {}", jp.getSignature());
    }
    
    // 実行前にログ出力する(Service)
    @Before("serviceMethods()")
    public void startServiceLog(JoinPoint jp) {
        log.debug("Service開始: {}", jp.getSignature());
    }
    
    // 実行後にログ出力する(Controller)
    @AfterReturning("controllerMethods()")
    public void endControllerLog(JoinPoint jp) {
        log.debug("Controller正常終了: {}", jp.getSignature());
    }
	
    // 実行後にログ出力する(Service)
    @AfterReturning("serviceMethods()")
    public void endServiceLog(JoinPoint jp) {
        log.debug("Service正常終了: {}", jp.getSignature());
    }
	
}
