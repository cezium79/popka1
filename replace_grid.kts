import java.io.File

fun main() {
    val file = File("C:/Android/Ohrana-c7953c45/app/src/main/java/com/example/ohrana/CloudStorageManager.kt")
    var content = file.readText()
    
    // === Замены CSS стилей (grid -> table) для generateHtmlReportWithDesign ===
    
    // 1. header-info grid -> table
    content = content.replace(
        ".header-info { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }",
        ".header-table { width: 100%; border-collapse: collapse; }"
    )
    content = content.replace(
        ".info-item { background: rgba(255,255,255,0.2); padding: 10px; border-radius: 4px; }",
        ".header-table td { background: rgba(255,255,255,0.2); padding: 10px; border-radius: 4px; text-align: center; vertical-align: middle; }"
    )
    content = content.replace(
        ".info-item label { font-size: 11px; opacity: 0.9; display: block; }",
        ".header-table td label { font-size: 11px; opacity: 0.9; display: block; }"
    )
    content = content.replace(
        ".info-item value { font-size: 13px; font-weight: bold; }",
        ".header-table td value { font-size: 13px; font-weight: bold; }"
    )
    
    // 2. round-info grid -> table
    content = content.replace(
        ".round-info { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 10px; }",
        ".round-info-table { width: 100%; border-collapse: collapse; margin-bottom: 10px; }"
    )
    content = content.replace(
        ".round-info-item { background: #fff; padding: 8px; border-radius: 4px; border: 1px solid #eee; text-align: center; }",
        ".round-info-table td { background: #fff; padding: 8px; border-radius: 4px; border: 1px solid #eee; text-align: center; vertical-align: middle; }"
    )
    content = content.replace(
        ".round-info-item label { font-size: 11px; color: #666; }",
        ".round-info-table td label { font-size: 11px; color: #666; display: block; }"
    )
    content = content.replace(
        ".round-info-item value { font-size: 14px; font-weight: bold; color: #333; }",
        ".round-info-table td value { font-size: 14px; font-weight: bold; color: #333; }"
    )
    
    // 3. stats-grid grid -> table
    content = content.replace(
        ".stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 15px; }",
        ".stats-table { width: 100%; border-collapse: collapse; margin-bottom: 15px; }"
    )
    
    // 4. incidents-grid grid -> table
    content = content.replace(
        """.incidents-grid {
                            display: grid;
                            grid-template-columns: repeat(3, 1fr);
                            gap: 15px;
                        }""",
        """.incidents-grid {
                            width: 100%;
                            border-collapse: collapse;
                        }
                        .incidents-grid td {
                            vertical-align: top;
                            width: 33.33%;
                            padding: 5px;
                            box-sizing: border-box;
                        }"""
    )
    
    // 5. checkpoint-grid grid -> table
    content = content.replace(
        """.checkpoint-grid {
                            display: grid;
                            grid-template-columns: repeat(3, 1fr);
                            gap: 15px;
                        }""",
        """.checkpoint-grid {
                            width: 100%;
                            border-collapse: collapse;
                        }
                        .checkpoint-grid td {
                            vertical-align: top;
                            width: 33.33%;
                            padding: 5px;
                            box-sizing: border-box;
                        }"""
    )
    
    // 6. Responsive media queries for incidents-grid
    content = content.replace(
        """.incidents-grid {
                                grid-template-columns: repeat(2, 1fr);
                            }""",
        """.incidents-grid td {
                                width: 50%;
                            }"""
    )
    content = content.replace(
        """.incidents-grid {
                                grid-template-columns: 1fr;
                            }""",
        """.incidents-grid td {
                                width: 100%;
                            }"""
    )
    
    file.writeText(content)
    println("CSS Grid replaced with HTML tables successfully!")
}
