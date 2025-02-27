package druid.elf.tool.util;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.YitIdHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

@SuppressWarnings("unused")
@Slf4j
public class UtilForData {

    /**
     * 根据属性值去重
     * @param keyExtractor 传入生成key值策略的的函数表达式
     * @param <T> 去重的类
     * @return boolean ture 不存在重复值  false 存在重复值
     */
    public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    /**
     * 通过状态码找到其对应的枚举值(注意状态码值必须在枚举中唯一，不然它会选择找到的第一条)
     * @param ts        枚举中的各个实例
     * @param function  获取比较码值的方法
     * @param r         状态码值
     * @return          枚举实例
     */
    public static <T extends Enum<?>, R> T getEnumByCode ( T[] ts, Function<T,R> function, R r ) {

        for ( T t : ts ) {
            if ( function.apply(t).equals(r)) return t;
        }
        log.warn("传入的状态码{}找不到对应的枚举",r);

        return null;
    }

    /**
     * 根据给出的路径匹配集合中的通配符，如果有一个通配符匹配上了那么就结束方法
     * @param patterns  通配符集合
     * @param path      给出的匹配路径
     * @return          true 匹配成功 false 匹配失败
     */
    public static boolean pathMatch(Collection<String> patterns,String path){

        for ( String pattern : patterns ) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            if ( antPathMatcher.match(pattern, path) ) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取公共的json转化对象
     */
    public static ObjectMapper getCommonObjectMapper(){

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    /**
     * 将json字符串转化为范型对象
     */
    public static <T> T transJsonStringToT( String jsonStr, Class<T> tClass ){

        if (StrUtil.isBlank(jsonStr)) return null;

        try {
            return getCommonObjectMapper().readValue(jsonStr,tClass);
        } catch (JsonProcessingException e) {
            log.warn("jsonStr read fail",e);
            return null;
        }
    }

    /**
     * 将Object类型的json对象（本质上是json字符串，只不过以对象的形式表达）转化为设定的泛型对象
     */
    public static <T> T transJsonObjectToT( Object jsonObject, Class<T> tClass ){

        if ( jsonObject == null ) return null;

        try {
            return transJsonStringToT(getCommonObjectMapper().writeValueAsString(jsonObject),tClass);
        } catch (JsonProcessingException e) {
            log.warn("jsonObject write fail",e);
            return null;
        }
    }

    /**
     * 对象转化成json字符串
     */
    public static String transObjectToJsonString( Object object ){

        if ( object == null ) return null;

        try {
            return getCommonObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.warn("jsonObject write fail",e);
            return null;
        }
    }

    static {
        // 创建 IdGeneratorOptions 对象，请在构造函数中输入 WorkerId：
        IdGeneratorOptions options = new IdGeneratorOptions((short) 1);
        // options.WorkerIdBitLength = 10; // WorkerIdBitLength 默认值6，支持的 WorkerId 最大值为2^6-1，若 WorkerId 超过64，可设置更大的 WorkerIdBitLength
        // ...... 其它参数设置参考 IdGeneratorOptions 定义，一般来说，只要再设置 WorkerIdBitLength （决定 WorkerId 的最大值）。
        // 保存参数（必须的操作，否则以上设置都不能生效）：
        YitIdHelper.setIdGenerator(options);
    }
    /**
     * 雪花算法生产id
     */
    public static String getSnowId(){

        // 以上初始化过程只需全局一次，且必"须在第2步之前设置。
        return YitIdHelper.nextId()+"";
    }

}
