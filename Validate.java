package com.xxx.xxx.annotation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.io.Serializable;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * 使用了此注解的类生成的对象可以对属性进行有效性校验
 * @author hhl
 * <p>Example1:
 * <pre>{@code
 * @Validate.Support(decs = "desc of XXX")
 * public class XXX {
 *    @Validate(desc = "合同编号", allowNull = false, size = @Validate.Size(min = 1, max = 20))   //不能为空（无条件），最小长度为1， 最大长度为20
 *    private String contractNo;
 *
 *    @Validate(desc = "业务标识", allowNull = false, size = @Validate.Size(min = 7, max = 7))   //长度必须为7
 *    private String trcd;
 *
 *    // 合同交易总金额，单位（元）
 *    @Validate(desc = "合同交易总金额", size = @Validate.Size(isNumber = true, integer = 16, fraction = 2)) //整数部分最大16位，小数部分最大2位
 *    private BigDecimal camount;
 * }
 * }</pre>
 * <p>Example2:
 * <pre>{@code
 * @Validate.Support(decs = "desc of XXX")
 * public class XXX {
 *    // 资格审核情况为“无需资质核验”时，必填
 *    @Validate(desc = "通讯地址", allowNull = false, spEL = "${buyerCheck == '0'}")   //不能为空（条件成立时）
 *    private String address;
 *    // 0-无需资质核验，1-需要资质核验
 *    @Validate(desc = "资格审核情况", allowNull = false)
 *    private String buyerCheck;
 * }
 * }</pre>
 * <p>Example3:
 * <pre>{@code
 * @Validate.Support(decs = "desc of XXX")
 * public class XXX {
 *     @Validate(
 *             desc = " ",
 *             message = "结束日期不符合yyyy-MM-dd格式",
 *             spEL = "${checkDateFormat(dateEnd)}"
 *     )
 *     @Validate(
 *             desc = " ",
 *             message = "结束日期应大于等于开始日期",
 *             spEL = "${checkDateEnd()}"
 *     )
 *     private String dateEnd;
 *
 *     public boolean checkDateFormat(String dateStr) {
 *         //...
 *     }
 *
 *     public boolean checkDateEnd() {
 *         //...
 *     }
 * }
 * }</pre>
 * <p>Example4:
 * <pre>{@code
 * @Validate.Support(desc = "付款状态查询")
 * public class XXX implements Serializable {
 *     private static final long serialVersionUID = 5426323079139451832L;
 *     // 提现业务流水号
 *     @Validate(desc = "提现业务流水号", size = @Validate.Size(max = 32))
 *     @Validate(desc = "提现业务流水号", spEL = "${!isEmpty(bsid) || !isEmpty(payno)}", message = "或付款单号不能为空")
 *     private String bsid;
 *     // 付款单号
 *     @Validate(desc = "付款单号", size = @Validate.Size(max = 19))
 *     private String payno;
 *
 *     public boolean isEmpty(Object object) {
 *         return Validate.Validator.isEmpty(object);
 *     }
 * }
 * }</pre>
 * <p>校验示例：
 * <pre>{@code
 * XXX xxx = new XXX();
 * // do xxx set
 * Validate.Result ret = Validate.Validator.validate(xxx);
 * }</pre>
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Validate.Array.class)
@Documented
public @interface Validate {
    Logger logger = LoggerFactory.getLogger(Validate.class);
    /**
     * boolean类型的SPEL表达式
     * @return
     */
    String spEL() default "${true}";

    String message() default "";

    String desc() default "";

    boolean allowNull() default true;

    Size size() default @Size();

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface Array {
        Validate[] value();
    }

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface Support {
        String desc() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface Size {
        Size DEFAULT = new Size() {
            /**
             * Returns the annotation type of this annotation.
             * @return the annotation type of this annotation
             */
            @Override
            public Class<? extends Annotation> annotationType() {
                return Size.class;
            }

            @Override
            public int min() {
                return 0;
            }

            @Override
            public int max() {
                return 2147483647;
            }

            @Override
            public int integer() {
                return 2147483647;
            }

            @Override
            public int fraction() {
                return 0;
            }

            @Override
            public boolean isNumber() {
                return false;
            }
        };

        int min() default 0;

        int max() default 2147483647;

        int integer() default 2147483647;

        int fraction() default 0;

        boolean isNumber() default false;
    }

    class Validator {

        public static Result validate(Object obj) {
            return validate(obj, null);
        }

        public static Result validate(Object obj, String desc) {
            Result validateResult = new Result();
            if (obj == null) {
                return validateResult;
            }
            Class cls = obj.getClass();
            if (cls.isAnnotationPresent(Support.class)) {
                Support support = (Support) cls.getAnnotation(Support.class);
                String value = support.desc();
                if (!StringUtils.isEmpty(value)) {
                    desc = value;
                }
            }
            validateResult.setDescription(desc);
            Stream<?> objStream = null;
            if (obj.getClass().isArray()) {
                objStream = Arrays.stream((Object[]) obj);
            }
            if (obj instanceof Collection) {
                objStream = ((Collection<?>) obj).stream();
            }
            if (objStream != null) {
                List<Result> results = Lists.newArrayList();
                String finalDesc = desc;
                objStream.forEach(o -> {
                    results.add(validate(o, finalDesc));
                });
                validateResult.addListResult(results);
                return validateResult;
            }
            Map<String, List<Result>> fieldResults = Maps.newHashMap();
            ReflectionUtils.doWithFields(
                    obj.getClass(),
                    field -> {
                        List<Validate> validates = Lists.newArrayList();
                        Array validateList = field.getAnnotation(Array.class);
                        Validate validate = field.getAnnotation(Validate.class);
                        if (validateList != null) {
                            validates.addAll(Lists.newArrayList(validateList.value()));
                        }
                        if (validate != null) {
                            validates.add(validate);
                        }
                        fieldResults.put(field.getName(), doValidate(validates, obj, field));
                    },
                    field -> field.isAnnotationPresent(Validate.class) || field.isAnnotationPresent(Array.class)
            );
            if (!CollectionUtils.isEmpty(fieldResults)) {
                fieldResults.keySet().forEach(key -> {
                    List<Result> results = fieldResults.get(key);
                    if (!CollectionUtils.isEmpty(results)) {
                        results
                                .stream()
                                .filter(result -> !result.isValid())
                                .forEach(result -> {
                                    validateResult.addResult(result);
                                });
                    }
                });
            }
            return validateResult;
        }

        public static List<Result> doValidate(List<Validate> validates, Object obj, Field field) {
            if (validates == null || obj == null || field == null) {
                return null;
            }
            AtomicReference<String> descRef = new AtomicReference<>("");
            validates.forEach(valid -> {
                if (StringUtils.isEmpty(descRef.get()) && !StringUtils.isEmpty(valid.desc())) {
                    descRef.set(valid.desc());
                }
            });
            if (StringUtils.isEmpty(descRef.get())) {
                descRef.set(field.getName());
            }
            List<Result> results = Lists.newArrayList();
            validates.forEach(validate -> results.add(executeValidate(validate, descRef.get(), obj, field)));
            return results;
        }

        public static Result executeValidate(Validate validate, String validateDesc, Object obj, Field field) {
            Result validateResult = new Result();
            if (validate == null || StringUtils.isEmpty(validate.spEL()) || obj == null || field == null) {
                return validateResult;
            }
            boolean ret = executeSPEl(validate.spEL(), obj);
            validateResult.setValid(validateResult.isValid() && ret);
            if (!ret && !StringUtils.isEmpty(validate.message())) {
                validateResult.addErrorMessage(validateDesc + validate.message());
                return validateResult;
            }
            Object curValue = null;
            try {
                field.setAccessible(true);
                curValue = field.get(obj);
            } catch (Exception e) {
                logger.warn("warn", e);
            }
            if (isEmpty(curValue)) {
                boolean allowNull = validate.allowNull();
                if (ret && !allowNull) {
                    validateResult.setValid(false);
                    validateResult.addErrorMessage(validateDesc + "不能为空");
                    return validateResult;
                }
                validateResult.setValid(true);
                return validateResult;
            }
            Class cls = field.getType();
            String clsName = field.getType().getName();
            String strValue = null;
            Size size = validate.size();
            boolean isNumber = false;
            if (isPrimitive(cls) || String.class.getName().equals(clsName) ) {
                strValue = "" + curValue;
                isNumber = isNumber(cls);
            }
            if (BigDecimal.class.getName().equals(clsName)) {
                strValue = ((BigDecimal) curValue).toPlainString();
                isNumber = true;
            }
            if (isNumber && !size.isNumber()) {
                size = new Size() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return validate.size().annotationType();
                    }

                    @Override
                    public int min() {
                        return validate.size().min();
                    }

                    @Override
                    public int max() {
                        return validate.size().max();
                    }

                    @Override
                    public int integer() {
                        return validate.size().integer();
                    }

                    @Override
                    public int fraction() {
                        return validate.size().fraction();
                    }

                    @Override
                    public boolean isNumber() {
                        return true;
                    }
                };
            }
            if (strValue != null) {
                boolean checkSize = checkSize(size, strValue, validateDesc, validateResult);
                validateResult.setValid(checkSize);
                return validateResult;
            }
            validateResult.addResult(validate(curValue, validateDesc));
            return validateResult;
        }

        public static boolean checkSize(Size size, String strValue, String validateDesc, Result validateResult) {
            if (size == null || strValue == null) {
                return true;
            }
            if (isDefaultSize(size)) {
                return true;
            }
            if (size.isNumber()) {
                int integer = Math.max(size.integer(), 1);
                int fraction = Math.max(size.fraction(), 0);
                BigDecimal bigDecimal = null;
                try {
                    bigDecimal = new BigDecimal(strValue);
                } catch (Exception e) {
                    logger.warn("warn", e);
                    validateResult.addErrorMessage(validateDesc + "无法转为数值");
                    return false;
                }
                int integerLen = 0;
                int fractionLen = 0;
                String[] bigDecimalStrAry = bigDecimal.toPlainString().split("\\.");
                if (bigDecimalStrAry.length > 0) {
                    integerLen = bigDecimalStrAry[0].length();
                    if (bigDecimalStrAry.length > 1) {
                        fractionLen = bigDecimalStrAry[1].length();
                    }
                }
                String errMsg = validateDesc;
                if (fraction == 0 && fractionLen > fraction) {
                    errMsg += "应为整数";
                    validateResult.addErrorMessage(errMsg);
                    return false;
                }
                if (integer == Size.DEFAULT.integer() && fraction == Size.DEFAULT.fraction()) {
                    return true;
                }
                if (integer != Size.DEFAULT.integer() && fraction != Size.DEFAULT.fraction()) {
                    if (integerLen > integer || fractionLen > fraction) {
                        errMsg += "长度不能超过<" + integer + "," + fraction +">";
                        validateResult.addErrorMessage(errMsg);
                        return false;
                    }
                    return true;
                }
                if (integer != Size.DEFAULT.integer()) {
                    if (integerLen > integer) {
                        errMsg += "整数部分长度不能超过" + integer;
                        validateResult.addErrorMessage(errMsg);
                        return false;
                    }
                    return true;
                }
                if (fractionLen > fraction) {
                    errMsg += "小数部分长度不能超过" + fraction;
                    validateResult.addErrorMessage(errMsg);
                    return false;
                }
                return true;
            } else {
                int max = Math.max(size.max(), 0);
                max = Math.min(max, Size.DEFAULT.max());
                int min = Math.max(size.min(), Size.DEFAULT.min());
                int tMax = Math.max(max, min);
                int tMin = Math.min(max, min);
                max = tMax;
                min = tMin;
                int len = realLength(strValue);
                String errMsg = validateDesc;
                if (max == Size.DEFAULT.max() && min == Size.DEFAULT.min()) {
                    return true;
                }
                if (max != Size.DEFAULT.max() && min != Size.DEFAULT.min()) {
                    if (len > max || len < min) {
                        if (max == min) {
                            errMsg += "长度必须为" + min;
                        } else {
                            errMsg += "长度须在" + min + "和" + max + "之间";
                        }
                        validateResult.addErrorMessage(errMsg);
                        return false;
                    }
                    return true;
                }
                if (max != Size.DEFAULT.max()) {
                    if (len > max) {
                        errMsg += "长度不能超过" + max;
                        validateResult.addErrorMessage(errMsg);
                        return false;
                    }
                    return true;
                }
                if (len < min) {
                    errMsg += "长度不能小于" + min;
                    validateResult.addErrorMessage(errMsg);
                    return false;
                }
                return true;
            }
        }

        public static boolean isDefaultSize(Size size) {
            return sizeEquals(size, Size.DEFAULT);
        }

        public static boolean sizeEquals(Size a, Size b) {
            if (a == null || b == null) {
                return a == b;
            }
            if (a == b) {
                return true;
            }
            return a.min() == b.min() && a.max() == b.max() && a.integer() == b.integer() && a.fraction() == b.fraction();
        }

        /**
         * 执行EL表达式
         * @param spel
         * @return
         */
        public static boolean executeSPEl(String spel, Object obj){
            if (StringUtils.isBlank(spel)){
                return false;
            }
            ExpressionParser parser = new SpelExpressionParser();
            StandardEvaluationContext context = new StandardEvaluationContext(obj);
            TemplateParserContext templateParserContext = new TemplateParserContext("${", "}");
            Expression expression = parser.parseExpression(spel, templateParserContext);
            boolean value = Boolean.TRUE.equals(expression.getValue(context, boolean.class));
            return value;
        }

        /**
         * 判断一个类是否是基本类型或基本类型的封装类型
         * @param cls
         * @return
         */
        public static boolean isPrimitive(Class<?> cls) {
            if (cls == null) {
                return false;
            }
            try {
                return ((Class<?>) cls.getField("TYPE").get(null)).isPrimitive();
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * 判断一个类是否是数值类型
         * @param cls
         * @return
         */
        private static boolean isNumber(Class<?> cls) {
            List<Class<?>> numberClasses = ImmutableList.of(int.class, double.class, float.class, long.class, short.class);
            if (isPrimitive(cls)) {
                try {
                    Class<?> checkClass = (Class<?>) cls.getField("TYPE").get(null);
                    return numberClasses.contains(checkClass);
                } catch (Exception e) {
                    return false;
                }
            }
            return false;
        }

        public static boolean isEmpty(Object object) {
            if (object == null || "".equals(object)) {
                return true;
            }
            if (object instanceof Collection) {
                return ((Collection<?>) object).isEmpty();
            }
            if (object instanceof Map) {
                return ((Map<?, ?>) object).isEmpty();
            }
            return false;
        }

        public static List<Field> getAllValidateFields(Class<?> cls) {
            List<Field> fields = Lists.newArrayList();
            while (cls != null) {
                ReflectionUtils.doWithFields(
                        cls,
                        field -> fields.add(field),
                        field -> field.isAnnotationPresent(Validate.class) || field.isAnnotationPresent(Array.class)
                );
                cls = cls.getSuperclass();
            }
            return fields;
        }

        /**
         * 计算字符串真实长度
         * 一个中文字符长度为2
         * @param str
         * @return
         */
        public static int realLength(String str) {
            if (isEmpty(str)) {
                return 0;
            }
            return str.replaceAll("[\\u0391-\\uFFE5]", "**").length();
        }

        /**
         * 获取对象为空的属性名称
         * @param source
         * @return
         */
        public static String[] getNullPropertyNames(Object source) {
            List<String> emptyNames = Lists.newArrayList();
            if (source == null) {
                return emptyNames.toArray(new String[]{});
            }
            ReflectionUtils.doWithFields(source.getClass(), field -> {
                ReflectionUtils.makeAccessible(field);
                if (ReflectionUtils.getField(field, source) == null) {
                    emptyNames.add(field.getName());
                }
            }, field -> {
                int modify = field.getModifiers();
                return !Modifier.isStatic(modify) && !Modifier.isFinal(modify);
            });
            return emptyNames.toArray(new String[]{});
        }
    }

    class Result implements Serializable {
        private static final long serialVersionUID = 750776830335701513L;
        private boolean valid = true;
        private String description;
        private List<String> errorMessages = Lists.newArrayList();

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public boolean isValid() {
            return valid;
        }

        public void addErrorMessage(String errorMsg) {
            if (!StringUtils.isEmpty(errorMsg)) {
                this.errorMessages.add(errorMsg);
                this.valid = false;
            }
        }

        public String getErrorMessage() {
            return StringUtils.join(errorMessages.toArray(), ",");
        }

        public void clear() {
            this.valid = true;
            this.errorMessages = Lists.newArrayList();
        }

        public void addResult(Result result) {
            addResult(result, false);
        }

        public void addResult(Result result, boolean isInArrayOrList) {
            if (result == null || result.isValid()) {
                return;
            }
            valid = false;
            String desc = result.getDescription();
            String msg = result.getErrorMessage();
            if (!StringUtils.isEmpty(msg)) {
                if (isInArrayOrList) {
                    msg = "{" + msg + "}";
                } else if (!StringUtils.isEmpty(desc)) {
                    msg = desc + "(" + msg + ")";
                }
                addErrorMessage(msg);
                result.clear();
            }
        }

        public void addListResult(List<Result> results) {
            if (CollectionUtils.isEmpty(results)) {
                return;
            }
            Result result = new Result();
            results.stream().forEach(res -> result.addResult(res, true));
            addResult(convertToListResult(result));
        }

        public Result convertToListResult(Result result) {
            Result res = new Result();
            res.setValid(result.isValid());
            res.setDescription(result.getDescription());
            String errMsg = result.getErrorMessage();
            if (!StringUtils.isEmpty(errMsg)) {
                res.addErrorMessage("[" + errMsg + "]");
            }
            return res;
        }
    }
}
