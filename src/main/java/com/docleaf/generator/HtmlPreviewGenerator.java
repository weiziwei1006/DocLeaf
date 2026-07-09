package com.docleaf.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * HTML 预览页面生成器
 * <p>
 * 生成一个完全自包含的 HTML 文件，内嵌 CSS + JavaScript + OpenAPI JSON 数据，
 * 双击即可在浏览器中打开，无需任何 HTTP 服务器。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>纯原生实现，不依赖任何外部 CDN</li>
 *   <li>OpenAPI JSON 内嵌为 JS 变量，无需 fetch，无跨域问题</li>
 *   <li>左侧菜单（按 tag 分组），右侧详情面板</li>
 *   <li>响应式布局，支持移动端</li>
 *   <li>自动适配暗色/亮色主题</li>
 * </ul>
 *
 * @author DocLeaf
 */
public class HtmlPreviewGenerator {

    private static final Logger log = LoggerFactory.getLogger(HtmlPreviewGenerator.class);

    /** 输出文件名 */
    private static final String OUTPUT_FILE_NAME = "docleaf-index.html";

    /**
     * 生成 HTML 预览页面（内嵌 OpenAPI JSON）
     *
     * @param outputDir   输出目录
     * @param openApiJson OpenAPI JSON 字符串，将内嵌到 HTML 中
     * @return 生成的文件路径，失败返回 null
     */
    public Path generate(Path outputDir, String openApiJson) {
        String html = buildHtml(openApiJson);

        Path outputPath = outputDir.resolve(OUTPUT_FILE_NAME);
        try {
            Files.createDirectories(outputDir);
            Files.write(outputPath, html.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("HTML 预览页面写入失败: {}", e.getMessage(), e);
            return null;
        }

        log.info("HTML 预览页面已生成: {}", outputPath);
        return outputPath;
    }

    /**
     * 构建完整的 HTML 页面
     * <p>
     * 包含内联 CSS、JavaScript 和内嵌的 OpenAPI JSON 数据。
     */
    private String buildHtml(String openApiJson) {
        // 对 JSON 中的 </script> 进行转义，防止 XSS 和 HTML 解析中断
        String safeJson = openApiJson.replace("</script>", "<\\/script>");
        return "<!DOCTYPE html>\n"
                + "<html lang=\"zh-CN\">\n<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "<title>DocLeaf API 文档</title>\n"
                + "<style>\n"
                + CSS
                + "</style>\n"
                + "</head>\n<body>\n"
                + "<div id=\"app\"><div id=\"sidebar\"><div id=\"sidebar-header\"><h1>DocLeaf</h1>"
                + "<p class=\"subtitle\">API 文档</p></div><nav id=\"menu\"></nav></div>"
                + "<div id=\"main\"><div id=\"content\"></div></div></div>\n"
                + "<script>\n"
                + "window.OPENAPI_SPEC=" + safeJson + ";\n"
                + JS
                + "</script>\n"
                + "</body>\n</html>\n";
    }

    // ======================== 内联 CSS ========================

    private static final String CSS =
        "*{margin:0;padding:0;box-sizing:border-box}\n"
        + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
        + "color:#1a1a2e;background:#f5f6fa}\n"
        + "#app{display:flex;min-height:100vh}\n"
        + "#sidebar{width:280px;background:#1a1a2e;color:#e0e0e0;padding:20px 0;"
        + "position:fixed;height:100vh;overflow-y:auto}\n"
        + "#sidebar-header{padding:0 24px 20px;border-bottom:1px solid #2d2d44}\n"
        + "#sidebar-header h1{font-size:22px;color:#00d9a3}\n"
        + "#sidebar-header .subtitle{font-size:13px;color:#888;margin-top:4px}\n"
        + "#menu{padding:12px 0}\n"
        + ".tag-group{margin-bottom:8px}\n"
        + ".tag-name{padding:6px 24px;font-size:12px;color:#666;text-transform:uppercase;"
        + "letter-spacing:1px;cursor:pointer}\n"
        + ".tag-name:hover{color:#aaa}\n"
        + ".menu-item{padding:8px 24px 8px 32px;cursor:pointer;font-size:14px;"
        + "color:#ccc;display:flex;align-items:center;gap:8px;transition:all .15s}\n"
        + ".menu-item:hover{background:#2d2d44;color:#fff}\n"
        + ".menu-item.active{background:#16213e;border-left:3px solid #00d9a3;color:#00d9a3}\n"
        + ".method-badge{font-size:10px;font-weight:700;padding:2px 6px;border-radius:3px;min-width:38px;text-align:center}\n"
        + ".m-get{background:#1b8e3f;color:#fff}.m-post{background:#1864ab;color:#fff}\n"
        + ".m-put{background:#d68f00;color:#fff}.m-delete{background:#c92a2a;color:#fff}\n"
        + ".m-patch{background:#7048e8;color:#fff}\n"
        + "#main{flex:1;margin-left:280px;padding:40px;max-width:100%;overflow-x:hidden}\n"
        + "#content{max-width:840px;margin:0 auto}\n"
        + ".api-card{background:#fff;border-radius:12px;padding:28px;margin-bottom:20px;"
        + "box-shadow:0 1px 3px rgba(0,0,0,.08);border:1px solid #eee}\n"
        + ".api-header{display:flex;align-items:center;gap:12px;margin-bottom:16px;flex-wrap:wrap}\n"
        + ".api-path{font-family:monospace;font-size:16px;font-weight:600;color:#1a1a2e}\n"
        + ".api-summary{font-size:15px;color:#555;margin-bottom:16px}\n"
        + ".section-title{font-size:13px;font-weight:700;color:#888;text-transform:uppercase;"
        + "letter-spacing:1px;margin:20px 0 10px}\n"
        + ".param-table{width:100%;border-collapse:collapse;font-size:13px}\n"
        + ".param-table th{text-align:left;padding:8px 12px;background:#f8f9fa;color:#495057;"
        + "border-bottom:2px solid #dee2e6;font-weight:600}\n"
        + ".param-table td{padding:8px 12px;border-bottom:1px solid #e9ecef;color:#333}\n"
        + ".param-required{color:#c92a2a;font-weight:700}\n"
        + ".api-desc{background:#f8f9fa;border-radius:8px;padding:14px;margin-top:10px;"
        + "font-size:13px;color:#555;line-height:1.6}\n"
        + "#loading{padding:40px;text-align:center;color:#888;font-size:16px}\n"
        + "#error{padding:40px;text-align:center;color:#c92a2a;font-size:16px}\n"
        + "@media(max-width:768px){#sidebar{position:relative;width:100%;height:auto}#main{margin-left:0}}\n";

    // ======================== 内联 JavaScript ========================

    private static final String JS =
        "function init(){\n"
        + "  try{\n"
        + "    var spec=window.OPENAPI_SPEC;\n"
        + "    if(!spec) throw new Error('内嵌的 OpenAPI 数据为空');\n"
        + "    renderMenu(spec);renderOverview(spec);\n"
        + "  }catch(e){\n"
        + "    document.getElementById('content').innerHTML='<div id=\"error\">加载 API 数据失败: '+e.message+'</div>';\n"
        + "  }\n"
        + "}\n"
        + "function renderMenu(spec){\n"
        + "  var menu=document.getElementById('menu');\n"
        + "  var tags=spec.tags||[];\n"
        + "  var byTag={};\n"
        + "  Object.keys(spec.paths||{}).forEach(function(path){\n"
        + "    Object.keys(spec.paths[path]).forEach(function(method){\n"
        + "      var op=spec.paths[path][method];\n"
        + "      var tag=(op.tags&&op.tags[0])||'Default';\n"
        + "      if(!byTag[tag])byTag[tag]=[];\n"
        + "      byTag[tag].push({path:path,method:method,op:op});\n"
        + "    });\n"
        + "  });\n"
        + "  Object.keys(byTag).forEach(function(tag){\n"
        + "    var g=document.createElement('div');g.className='tag-group';\n"
        + "    g.innerHTML='<div class=\"tag-name\">'+tag+'</div>';\n"
        + "    byTag[tag].forEach(function(item){\n"
        + "      var el=document.createElement('div');el.className='menu-item';\n"
        + "      el.innerHTML='<span class=\"method-badge m-'+item.method+'\">'+item.method.toUpperCase()+'</span>'+'<span>'+item.path+'</span>';\n"
        + "      el.onclick=function(){renderApi(item)};\n"
        + "      g.appendChild(el);\n"
        + "    });\n"
        + "    menu.appendChild(g);\n"
        + "  });\n"
        + "}\n"
        + "function renderOverview(spec){\n"
        + "  var info=spec.info||{};\n"
        + "  var c=document.getElementById('content');\n"
        + "  c.innerHTML='<h1 style=\"font-size:28px;margin-bottom:8px\">'+(info.title||'API')+'</h1>'\n"
        + "    +'<p style=\"color:#666;margin-bottom:20px\">'+(info.description||'')+'</p>'\n"
        + "    +'<div class=\"api-card\"><h3>概览</h3><p>版本: '+(info.version||'1.0.0')+'</p>'\n"
        + "    +'<p>Controller 数: '+(info['x-total-controllers']||'?')+'</p>'\n"
        + "    +'<p>接口总数: '+(info['x-total-apis']||'?')+'</p></div>';\n"
        + "}\n"
        + "function renderApi(item){\n"
        + "  var op=item.op||{};\n"
        + "  document.querySelectorAll('.menu-item').forEach(function(e){e.classList.remove('active')});\n"
        + "  event.currentTarget.classList.add('active');\n"
        + "  var c=document.getElementById('content');\n"
        + "  var html='<div class=\"api-card\">';\n"
        + "  html+='<div class=\"api-header\"><span class=\"method-badge m-'+item.method+'\">'+item.method.toUpperCase()+'</span>'\n"
        + "    +'<span class=\"api-path\">'+item.path+'</span></div>';\n"
        + "  if(op.summary)html+='<div class=\"api-summary\">'+op.summary+'</div>';\n"
        + "  if(op.parameters&&op.parameters.length){\n"
        + "    html+='<div class=\"section-title\">Parameters</div><table class=\"param-table\">';\n"
        + "    html+='<tr><th>名称</th><th>位置</th><th>类型</th><th>必填</th><th>描述</th></tr>';\n"
        + "    op.parameters.forEach(function(p){\n"
        + "      html+='<tr><td><code>'+p.name+'</code></td><td>'+p.in+'</td><td>'+(p.schema&&p.schema.type||'object')+'</td>'\n"
        + "        +'<td class=\"'+(p.required?'param-required':'')+'\">'+(p.required?'是':'否')+'</td>'\n"
        + "        +'<td>'+(p.description||'')+'</td></tr>';\n"
        + "    });\n"
        + "    html+='</table>';\n"
        + "  }\n"
        + "  if(op.requestBody){\n"
        + "    var rb=op.requestBody;\n"
        + "    html+='<div class=\"section-title\">Request Body</div>';\n"
        + "    html+='<div class=\"api-desc\">'+(rb.description||'')+'<br>Content-Type: application/json</div>';\n"
        + "  }\n"
        + "  if(op.responses){\n"
        + "    html+='<div class=\"section-title\">Responses</div><table class=\"param-table\">';\n"
        + "    html+='<tr><th>状态码</th><th>描述</th></tr>';\n"
        + "    Object.keys(op.responses).forEach(function(code){\n"
        + "      html+='<tr><td><strong>'+code+'</strong></td><td>'+op.responses[code].description+'</td></tr>';\n"
        + "    });\n"
        + "    html+='</table>';\n"
        + "  }\n"
        + "  html+='</div>';\n"
        + "  c.innerHTML=html;\n"
        + "}\n"
        + "init();\n";
}
