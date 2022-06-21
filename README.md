# Validate
# 使用了Validate注解的类生成的对象可以对属性进行有效性校验

------

### 1. 执行校验
```java
XXX xxx = new XXX();
// do xxx set
Validate.Result ret = Validate.Validator.validate(xxx);
```
### 2. Example1:
```java
 @Validate.Support(decs = "desc of XXX")
 public class XXX {
    @Validate(desc = "合同编号", allowNull = false, size = @Validate.Size(min = 1, max = 20))   //不能为空（无条件），最小长度为1， 最大长度为20
    private String contractNo;
 
    @Validate(desc = "业务标识", allowNull = false, size = @Validate.Size(min = 7, max = 7))   //长度必须为7
    private String trcd;
 
    // 合同交易总金额，单位（元）
    @Validate(desc = "合同交易总金额", size = @Validate.Size(isNumber = true, integer = 16, fraction = 2)) //整数部分最大16位，小数部分最大2位
    private BigDecimal camount;
 }
```
### 3. Example2:
```java
 @Validate.Support(decs = "desc of XXX")
 public class XXX {
    // 资格审核情况为“无需资质核验”时，必填
    @Validate(desc = "通讯地址", allowNull = false, spEL = "${buyerCheck == '0'}")   //不能为空（条件成立时）
    private String address;
    // 0-无需资质核验，1-需要资质核验
    @Validate(desc = "资格审核情况", allowNull = false)
    private String buyerCheck;
 }
```
### 4. Example3:
```java
@Validate.Support(decs = "desc of XXX")
 public class XXX {
     @Validate(
             desc = " ",
             message = "结束日期不符合yyyy-MM-dd格式",
             spEL = "${checkDateFormat(dateEnd)}"
     )
     @Validate(
             desc = " ",
             message = "结束日期应大于等于开始日期",
             spEL = "${checkDateEnd()}"
     )
     private String dateEnd;
 
     public boolean checkDateFormat(String dateStr) {
         //...
     }
 
     public boolean checkDateEnd() {
         //...
     }
 }
```
### 5. Example4:
```java
@Validate.Support(desc = "付款状态查询")
 public class XXX implements Serializable {
     private static final long serialVersionUID = 5426323079139451832L;
     // 提现业务流水号, 条件必输，提现业务流水号和付款单号必须提供其中一个
     @Validate(desc = "提现业务流水号", size = @Validate.Size(max = 32))
     @Validate(desc = "提现业务流水号", spEL = "${!isEmpty(bsid) || !isEmpty(payno)}", message = "或付款单号不能为空")
     private String bsid;
     // 付款单号, 条件必输，提现业务流水号和付款单号必须提供其中一个
     @Validate(desc = "付款单号", size = @Validate.Size(max = 19))
     private String payno;
 
     public boolean isEmpty(Object object) {
         return Validate.Validator.isEmpty(object);
     }
 }
```

