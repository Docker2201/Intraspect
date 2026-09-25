package com.sergey.pisarev.controller;

import com.sergey.pisarev.ai.*;
import com.sergey.pisarev.util.MiniJson;
import java.lang.reflect.Proxy;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercise actual HTTP authorization and nested, machine-readable live state. */
public final class SimulationMcpRegressionTest {
    public static void main(String[] args)throws Exception {
        System.setProperty("chekator.data.dir",Files.createTempDirectory(Path.of(args[0]),"mcp-live-").toString());
        var calls=new AtomicInteger();
        var app=(AppAccess)Proxy.newProxyInstance(AppAccess.class.getClassLoader(),new Class<?>[]{AppAccess.class},(proxy,method,a)->switch(method.getName()) {
            case "simulationState" -> Map.of("open",true,"renderedSeconds",12.25,"channels",List.of(Map.of("support",2,"blockText","N10 X=\"test\"")));
            case "controlSimulation" -> { calls.incrementAndGet();yield Map.of("meshPending",true); }
            case "simulationView" -> AppAccess.Picture.failed("Окно закрыто");
            default -> null;
        });
        var server=McpServer.start(app,0,McpServer.Access.READ_ONLY,"test");
        try {
            var read=call(server,"get_simulation_state");
            check(!Boolean.TRUE.equals(read.get("isError")),"read-only allows live read");
            var content=(List<?>)read.get("content");var state=MiniJson.parseObject((String)((Map<?,?>)content.get(0)).get("text"));
            check(((List<?>)state.get("channels")).size()==1 && (double)state.get("renderedSeconds")==12.25,"nested state parses as JSON");
            check(Boolean.TRUE.equals(call(server,"control_simulation").get("isError"))&&calls.get()==0,"read-only rejects all cycle mutations");
            server.setAccess(McpServer.Access.FULL);
            check(!Boolean.TRUE.equals(call(server,"control_simulation").get("isError"))&&calls.get()==1,"full access controls cycle");
            check(Boolean.TRUE.equals(call(server,"render_simulation_view").get("isError")),"missing live window is explicit error, never final-model fallback");
            var names=MiniJson.parseObjectArray(McpTools.listJson()).stream().map(m->m.get("name")).toList();
            check(names.containsAll(List.of("get_simulation_state","control_simulation","render_simulation_view")),"live tools discoverable");
        } finally {McpServer.stop();}
        System.out.println("Live simulation MCP wire regressions passed.");
    }
    static Map<String,Object> call(McpServer server,String name)throws Exception {
        String body="{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\""+name+"\",\"arguments\":{\"action\":\"seek\",\"seconds\":12.25}}}";
        var connection=(HttpURLConnection)URI.create("http://127.0.0.1:"+server.port()+"/mcp").toURL().openConnection();
        connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setReadTimeout(5000);
        connection.setRequestProperty("Content-Type","application/json");
        connection.setRequestProperty("Authorization","Bearer "+server.token());
        try {
            try(var out=connection.getOutputStream()){out.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            check(connection.getResponseCode()==200,"HTTP success");
            try(var in=connection.getInputStream()) {
                return (Map<String,Object>)MiniJson.parseObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).get("result");
            }
        } finally {connection.disconnect();}
    }
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
