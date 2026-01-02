package com.tanasi.retrofit_jsoup.converter

import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import retrofit2.Converter
import java.nio.charset.Charset

class JsoupConverter(
    private val baseUri: String,
) : Converter<ResponseBody, Document?> {

    override fun convert(value: ResponseBody): Document? {
        val contentType = value.contentType()
        val isXml = contentType?.subtype?.contains("xml", ignoreCase = true) == true
        val parser = if (isXml) Parser.xmlParser() else Parser.htmlParser()

        val body = value.string()

        return Jsoup.parse(body, baseUri, parser)
    }
}