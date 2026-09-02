package org.jeecg.modules.bems.mdm.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.integration.config.IntegrationProperties;
import org.jeecg.modules.bems.patterned.entity.QualityStamp;
import org.jeecg.modules.bems.patterned.service.IQualityStampService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 燃气和氢气,以及其它
 */
@Api(tags = "燃气和氢气数据")
@RestController
@RequestMapping("/bems/gasAndHydrogen")
@AllArgsConstructor
@Slf4j
public class GasAndHydrogenController {

    private final IntegrationProperties props;

    private final IQualityStampService qualityStampService;

    @ApiOperation(value = "获取燃气数据", notes = "获取燃气数据")
    @GetMapping("/queryGasData")
    public Result<Object> queryGasData(){
        try {
            return post(props.getGas());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @ApiOperation(value = "获取氢气数据", notes = "获取氢气数据")
    @GetMapping("/queryHydrogenData")
    public Result<Object> queryHydrogenData(){
        try {
            return post(props.getHydrogen());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取二楼电表数据", notes = "获取二楼电表数据")
    @GetMapping("/queryEldbData")
    public Result<Object> queryEldbData(){
        try {
            return post(props.getEldb());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取雨水处理器数据", notes = "获取雨水处理器数据")
    @GetMapping("/queryYsclqData")
    public Result<Object> queryYsclqData(){
        try {
            return post(props.getYsclq());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取1#锅炉数据", notes = "获取1#锅炉数据")
    @GetMapping("/queryGl1Data")
    public Result<Object> queryGl1Data(){
        try {
            return post(props.getGl1());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取2#锅炉数据", notes = "获取2#锅炉数据")
    @GetMapping("/queryGl2Data")
    public Result<Object> queryGl2Data(){
        try {
            return post(props.getGl2());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取3#锅炉数据", notes = "获取3#锅炉数据")
    @GetMapping("/queryGl3Data")
    public Result<Object> queryGl3Data(){
        try {
            return post(props.getGl3());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取锅炉辅机数据", notes = "获取锅炉辅机数据")
    @GetMapping("/queryGlfjData")
    public Result<Object> queryGlfjData(){
        try {
            return post(props.getGlfj());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取北方稀土水处理数据", notes = "获取北方稀土水处理数据")
    @GetMapping("/queryBfxtsclData")
    public Result<Object> queryBfxtsclData(){
        try {
            return post(props.getBfxtscl());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取零氮数据", notes = "获取零氮数据")
    @GetMapping("/queryLdData")
    public Result<Object> queryLdData(){
        try {
            return post(props.getLd());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取掺氢-混气数据", notes = "获取掺氢-混气数据")
    @GetMapping("/queryCqhqData")
    public Result<Object> queryCqhqData(){
        try {
            return post(props.getCqhq());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取掺氢氢气减压撬数据", notes = "获取掺氢氢气减压撬数据")
    @GetMapping("/queryCqqqjyqData")
    public Result<Object> queryCqqqjyqData(){
        try {
            return post(props.getCqqqjyq());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取光伏数据", notes = "获取光伏数据")
    @GetMapping("/queryGfData")
    public Result<Object> queryGfData(){
        try {
            return post(props.getGf());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取光热系统主机数据", notes = "获取光热系统主机数据")
    @GetMapping("/queryGrxtzjData")
    public Result<Object> queryGrxtzjData(){
        try {
            return post(props.getGrxtzj());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取bems数据", notes = "获取bems数据")
    @GetMapping("/queryBemsData")
    public Result<Object> queryBemsData(){
        try {
            return post(props.getBems());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @ApiOperation(value = "获取能源站数据", notes = "获取能源站数据")
    @GetMapping("/queryNyzData")
    public Result<Object> queryNyzData(){
        try {
            return post(props.getNyz());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Result<Object> post(IntegrationProperties.GasOrHydrogen gasOrHydrogen) throws Exception{
        String url = gasOrHydrogen.getUrl();

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        con.setRequestMethod("POST");

        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");

        String tagids = StringUtils.isBlank(gasOrHydrogen.getTagids())
                ?"*":gasOrHydrogen.getTagids();
        String requestBody = "{\"tagids\":\"" + tagids+"\"," +
                    "\"charset\": \"utf-8\", \"archived\":0}";
        con.setDoInput(true);
        con.setDoOutput(true);

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(con.getOutputStream(), StandardCharsets.UTF_8));
        out.write(requestBody);
        out.newLine();
        out.flush();
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String responseLine = null;
        while ((responseLine = br.readLine()) != null) {
            response.append(responseLine.trim());
        }
        br.close();
        out.close();
        con.disconnect();
        JSONObject json = JSONObject.parseObject(response.toString());
        if(json.getJSONObject("data")==null){
            return Result.error("没有数据");
        }
        //加入接口Point获取desc
        StringBuilder tagidsBuilder = new StringBuilder();
        for(JSONObject jsonObject : json.getJSONObject("data")
                .getJSONArray("values").toArray(new JSONObject[0])){
            tagidsBuilder.append(jsonObject.getLong("pid"));
            tagidsBuilder.append(",");
        }
        String requestPointBody = tagidsBuilder.substring(0, tagidsBuilder.lastIndexOf(","));
        try{
            url = props.getPoint().getUrl();
            obj = new URL(url);
            con = (HttpURLConnection) obj.openConnection();

            con.setRequestMethod("POST");

            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");

            requestPointBody = "{\"tagids\":\"" + requestPointBody+"\"," +
                    "\"charset\": \"utf-8\", \"archived\":0," +
                    "\"fieldnames\":\"tagid,longname,desc\"}";
            con.setDoInput(true);
            con.setDoOutput(true);

            out = new BufferedWriter(new OutputStreamWriter(con.getOutputStream(), StandardCharsets.UTF_8));
            out.write(requestPointBody);
            out.newLine();
            out.flush();
            br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            response = new StringBuilder();
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            JSONObject descResponse = JSONObject.parseObject(response.toString());
            //读取 质量戳 从数据库表加载
            List<QualityStamp> list = qualityStampService.getList();
            for(JSONObject object : json.getJSONObject("data")
                    .getJSONArray("values").toArray(new JSONObject[0])){
                //循环赋值
                for(QualityStamp qs : list){
                    if(qs.getID().equalsIgnoreCase(object.getString("qy"))){
                        object.put("qy", qs.getName());
                        break;
                    }
                }
                //获取描述字符串
                for(JSONObject descObject : descResponse.getJSONObject("data")
                        .getJSONArray("values").toArray(new JSONObject[0])){
                    if(object.getString("pid").equals(descObject.getString("TagId"))){
                        object.put("desc", descObject.getString("Desc"));
                        break;
                    }
                }
            }
            br.close();
            out.close();
            con.disconnect();
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            if(br!=null) {
                br.close();
                br = null;
            }
            if(out!=null) {
                out.close();
                out = null;
            }
            if(con!=null){
                con.disconnect();
                con = null;
            }
        }
        return Result.OK(json.get("data"));
    }

    public Result<Object> postTest(IntegrationProperties.GasOrHydrogen gasOrHydrogen){
        com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject("{\"code\":0,\"mesg\":\"succeed\",\"data\":{\"count\":2,\"values\":[{\"pid\":221968,\"name\":\"/河北/保定/PowerSwitch\",\"pv\":true,\"tm\":\"0\",\"qy\":32},{\"pid\":221969,\"name\":\"/河北/保定/TEMP\",\"pv\":0.000000,\"tm\":\"0\",\"qy\":32}]}}");
//        JSONObject json = JSONObject.parseObject("{\"name\":\"test\"}");
        return Result.OK(json.get("data"));
    }

    public String postTT(IntegrationProperties.GasOrHydrogen gasOrHydrogen){
        return "{\n" +
                "    \"success\": true,\n" +
                "    \"message\": \"\",\n" +
                "    \"code\": 200,\n" +
                "    \"result\": {\n" +
                "        \"records\": [\n" +
                "            {\n" +
                "                \"id\": 2,\n" +
                "                \"createBy\": \"fangz0025\",\n" +
                "                \"createTime\": \"2026-05-29 09:25:50\",\n" +
                "                \"updateBy\": \"fangz0025\",\n" +
                "                \"updateTime\": \"2026-05-29 09:30:45\",\n" +
                "                \"sysOrgCode\": \"A01A03\",\n" +
                "                \"pageNo\": 1,\n" +
                "                \"pageSize\": 10,\n" +
                "                \"strategyCode\": \"DX1780017950086\",\n" +
                "                \"strategyName\": \"大厅温湿度联控\",\n" +
                "                \"strategyTarget\": \"根据现场环境控制\",\n" +
                "                \"frontDevice\": \"空气质量_PM-T1F1-01\",\n" +
                "                \"rearDevice\": \"风机盘管_T1楼1层主电梯厅外侧,风机盘管_T1楼1层主电梯厅外侧\",\n" +
                "                \"enabledStatus\": \"0\",\n" +
                "                \"frontPointList\": null,\n" +
                "                \"rearPointList\": null\n" +
                "            }\n" +
                "        ],\n" +
                "        \"total\": 1,\n" +
                "        \"size\": 10,\n" +
                "        \"current\": 1,\n" +
                "        \"orders\": [],\n" +
                "        \"optimizeCountSql\": true,\n" +
                "        \"searchCount\": true,\n" +
                "        \"maxLimit\": null,\n" +
                "        \"countId\": null,\n" +
                "        \"pages\": 1\n" +
                "    },\n" +
                "    \"timestamp\": 1788077704808\n" +
                "}";
    }
}
