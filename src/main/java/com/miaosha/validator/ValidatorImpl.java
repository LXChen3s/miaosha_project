package com.miaosha.validator;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

@Component
public class ValidatorImpl implements InitializingBean {

    private Validator validator;

    // 实现校验方法，返回校验结果
    public ValidationResult validate(Object bean){
        final ValidationResult validationResult=new ValidationResult();
        Set<ConstraintViolation<Object>> errSet=validator.validate(bean);
        if(errSet.size()>0){
            validationResult.setHasErr(true);
            errSet.forEach(constraintViolation->{
                String errMsg=constraintViolation.getMessage();
                String propertyName=constraintViolation.getPropertyPath().toString();
                validationResult.getErrMsgMap().put(propertyName,errMsg);
            });
        }
        return validationResult;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 获取hibernate validator实例
        validator=Validation.buildDefaultValidatorFactory().getValidator();
    }
}
