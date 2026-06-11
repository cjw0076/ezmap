package com.example.ez_capstone.mcp

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class McpProtocolTest {

    private lateinit var server: EZmapMcpServer

    @Before
    fun setUp() {
        server = EZmapMcpServer(McpToolRegistry(), toolExecutor = null)
    }

    @Test
    fun `initialize returns server info`() {
        val req = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""
        val res = server.handleMessage(req)
        assertNotNull(res)
        assertTrue(res!!.contains("EZmap"))
        assertTrue(res.contains("\"id\":1"))
    }

    @Test
    fun `tools_list returns registered tools`() {
        val req = """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}"""
        val res = server.handleMessage(req)
        assertNotNull(res)
        assertTrue(res!!.contains("search_places"))
        assertTrue(res.contains("get_directions"))
    }

    @Test
    fun `unknown method returns error -32601`() {
        val req = """{"jsonrpc":"2.0","id":3,"method":"no_such_method","params":{}}"""
        val res = server.handleMessage(req)
        assertTrue(res!!.contains("-32601"))
    }

    @Test
    fun `sync tools_call returns explicit async handler error`() {
        val req = """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_weather","arguments":{}}}"""
        val res = server.handleMessage(req)
        assertTrue(res!!.contains("-32000"))
        assertTrue(res.contains("async MCP handler"))
    }

    @Test
    fun `malformed JSON returns parse error -32700`() {
        val res = server.handleMessage("{bad json{{")
        assertTrue(res!!.contains("-32700"))
    }
}
