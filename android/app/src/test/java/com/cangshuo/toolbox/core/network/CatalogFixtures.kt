package com.cangshuo.toolbox.core.network

internal const val CATALOG_TRACE = "0123456789abcdef0123456789abcdef"

internal fun catalogRecord(code: String = "web_qr", mode: String = "WEB", name: String = "二维码工作台"): String =
    """{"code":"$code","name":"$name","description":"生成二维码","categoryCode":"QR","icon":null,"keywords":["qr"],"mode":"$mode","requiresLogin":false,"status":"ENABLED","version":1,"sortOrder":0,"isFeatured":false}"""

internal fun catalogPage(
    records: List<String> = listOf(catalogRecord()),
    page: Int = 1,
    pageSize: Int = 100,
    total: Long = records.size.toLong(),
): String = """{"code":0,"message":"success","traceId":"$CATALOG_TRACE","data":{"records":[${records.joinToString(",")}],"page":$page,"pageSize":$pageSize,"total":$total}}"""
