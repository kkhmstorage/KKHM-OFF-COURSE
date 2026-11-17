package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.CIC_DAYS
import com.example.data.model.CIC_PERIODS
import com.example.data.model.OffCourseRecord
import com.example.data.model.WeeklyStudyReport
import java.io.File
import java.io.FileOutputStream

object OffCoursePdfExporter {

    private const val PAGE_WIDTH = 595 // A4 points
    private const val PAGE_HEIGHT = 842

    fun exportAndShareRecordPdf(
        context: Context,
        record: OffCourseRecord,
        reports: List<WeeklyStudyReport>
    ): Result<Unit> {
        return try {
            val document = PdfDocument()

            // PAGE 1: Cover & Student Details (Mirrors PDF Page 1)
            val page1 = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            drawPage1(page1.canvas, record)
            document.finishPage(page1)

            // PAGE 2: Subject Details, Timetable & Signatures (Mirrors PDF Page 2)
            val page2 = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create())
            drawPage2(page2.canvas, record)
            document.finishPage(page2)

            // PAGE 3: Study Report Weeks 1..9 (Mirrors PDF Page 3)
            val page3 = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 3).create())
            drawReportPage(page3.canvas, record, reports, 1..9, 3)
            document.finishPage(page3)

            // PAGE 4: Study Report Weeks 10..18 (Mirrors PDF Page 4)
            val page4 = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 4).create())
            drawReportPage(page4.canvas, record, reports, 10..18, 4)
            document.finishPage(page4)

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeFileName = "KKHM_OffCourse_${record.cicAdNo.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.pdf"
            val pdfFile = File(exportDir, safeFileName)
            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "KKHM Islamic & Arts College Off-Course Record - ${record.studentName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Export / Share KKHM Off-Course Record PDF")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun drawPage1(canvas: Canvas, record: OffCourseRecord) {
        val margin = 42f
        val borderPaint = Paint().apply {
            color = Color.rgb(13, 92, 75)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val titlePaint = Paint().apply {
            color = Color.rgb(13, 92, 75)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val collegePaint = Paint().apply {
            color = Color.rgb(13, 92, 75)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint().apply {
            color = Color.rgb(30, 35, 34)
            textSize = 11f
        }

        canvas.drawRect(margin, margin, PAGE_WIDTH - margin, 460f, borderPaint)
        canvas.drawText("KKHM ISLAMIC & ARTS COLLEGE", PAGE_WIDTH / 2f, 165f, collegePaint)
        canvas.drawText("Affiliated to Coordination of Islamic Colleges (CIC)", PAGE_WIDTH / 2f, 184f, subPaint)
        canvas.drawText("OFF COURSE RECORD", PAGE_WIDTH / 2f, 255f, titlePaint)
        canvas.drawText(record.subjectTitle, PAGE_WIDTH / 2f, 290f, Paint(subPaint).apply {
            textSize = 14f
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        canvas.drawText(
            "Utilization: ${record.utilizationPercent}% (${record.totalUtilizedPeriods}/${record.totalAllocatedPeriods} Periods)",
            PAGE_WIDTH / 2f,
            320f,
            subPaint
        )

        var y = 490f
        val rowH = 36f
        val col1W = 155f
        val tableW = PAGE_WIDTH - 2 * margin

        fun drawRow(label: String, value: String) {
            canvas.drawRect(margin, y, margin + col1W, y + rowH, borderPaint)
            canvas.drawRect(margin + col1W, y, margin + tableW, y + rowH, borderPaint)
            canvas.drawText(label, margin + 8f, y + 22f, labelPaint)
            canvas.drawText(value.take(58), margin + col1W + 8f, y + 22f, valuePaint)
            y += rowH
        }

        drawRow("Name of Student", record.studentName)
        drawRow("CIC Ad No / Clg Ad No", "${record.cicAdNo}   |   Clg: ${record.clgAdNo.ifBlank { "-" }}")
        drawRow("Class / Department", record.classDepartment)
        drawRow("Academic Year / Sem", "${record.academicYear}   |   Semester: ${record.semester}")
        drawRow("Programme Stream", record.stream)
        drawRow("College Affl. & Name", record.collegeAfflName)

        y += 38f
        canvas.drawText("KKHM ISLAMIC & ARTS COLLEGE • CIC AFFILIATED", PAGE_WIDTH / 2f, y, Paint(subPaint).apply {
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(13, 92, 75)
        })
        canvas.drawText("Coordination of Islamic Colleges (CIC) • ISO 9001:2015 Certified", PAGE_WIDTH / 2f, y + 16f, subPaint)
    }

    private fun drawPage2(canvas: Canvas, record: OffCourseRecord) {
        val margin = 42f
        val tableW = PAGE_WIDTH - 2 * margin
        val borderPaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val headerPaint = Paint().apply {
            color = Color.rgb(13, 92, 75)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint().apply {
            color = Color.BLACK
            textSize = 10.5f
        }

        var y = 48f
        canvas.drawRect(margin, y, margin + tableW, y + 26f, borderPaint)
        canvas.drawText("Subject Details", PAGE_WIDTH / 2f, y + 17f, headerPaint)
        y += 26f

        fun drawDetailRow(label: String, value: String, height: Float = 30f) {
            canvas.drawRect(margin, y, margin + 150f, y + height, borderPaint)
            canvas.drawRect(margin + 150f, y, margin + tableW, y + height, borderPaint)
            canvas.drawText(label, margin + 8f, y + 19f, labelPaint)
            canvas.drawText(value.take(62), margin + 158f, y + 19f, valuePaint)
            y += height
        }

        drawDetailRow("Title", record.subjectTitle)
        drawDetailRow("Proposal App No / Credit", "${record.proposalAppNo.ifBlank { "-" }}   |   Credit: ${record.credit}")
        drawDetailRow("Name of Guide", record.guideName)
        drawDetailRow("Phone / WhatsApp", "${record.guidePhone.ifBlank { "-" }}   |   WA: ${record.guideWhatsapp.ifBlank { "-" }}")
        drawDetailRow("Reference", record.referenceBooks.ifBlank { "-" }, height = 56f)

        y += 28f
        canvas.drawRect(margin, y, margin + tableW, y + 26f, borderPaint)
        canvas.drawText("Timetable", PAGE_WIDTH / 2f, y + 17f, headerPaint)
        y += 26f

        val dayColW = 100f
        val periodColW = (tableW - dayColW) / CIC_PERIODS.size
        val cellH = 30f

        // Header row: Days | 1 | 2 | 3 | 5 | 6 | 7 | 8
        canvas.drawRect(margin, y, margin + dayColW, y + cellH, borderPaint)
        canvas.drawText("Days", margin + 32f, y + 19f, labelPaint)
        CIC_PERIODS.forEachIndexed { idx, period ->
            val x = margin + dayColW + idx * periodColW
            canvas.drawRect(x, y, x + periodColW, y + cellH, borderPaint)
            canvas.drawText(period.toString(), x + periodColW / 2f - 4f, y + 19f, labelPaint)
        }
        y += cellH

        val activePaint = Paint().apply {
            color = Color.rgb(13, 92, 75)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        CIC_DAYS.forEach { day ->
            canvas.drawRect(margin, y, margin + dayColW, y + cellH, borderPaint)
            canvas.drawText(day, margin + 10f, y + 19f, valuePaint)
            CIC_PERIODS.forEachIndexed { idx, period ->
                val x = margin + dayColW + idx * periodColW
                canvas.drawRect(x, y, x + periodColW, y + cellH, borderPaint)
                if ("$day:$period" in record.timetableSlots) {
                    canvas.drawText("✓", x + periodColW / 2f, y + 19f, activePaint)
                }
            }
            y += cellH
        }

        // Period per week footer
        canvas.drawRect(margin, y, margin + tableW - 100f, y + 30f, borderPaint)
        canvas.drawRect(margin + tableW - 100f, y, margin + tableW, y + 30f, borderPaint)
        canvas.drawText("Period per Week", PAGE_WIDTH / 2f - 40f, y + 19f, labelPaint)
        canvas.drawText(record.periodsPerWeek.toString(), margin + tableW - 55f, y + 19f, labelPaint)

        y += 95f
        val guideSig = "${record.guideSignatureName.ifBlank { record.guideName }} (${record.guideSignatureStatus})"
        val prinSig = "${record.principalSignatureName.ifBlank { "Principal / Dean" }} (${record.principalSignatureStatus})"
        canvas.drawText("Guide & Signature: $guideSig", margin, y, valuePaint)
        canvas.drawText("Principal / Dean: $prinSig", margin, y + 22f, valuePaint)
    }

    private fun drawReportPage(
        canvas: Canvas,
        record: OffCourseRecord,
        reports: List<WeeklyStudyReport>,
        weekRange: IntRange,
        pageNumber: Int
    ) {
        val margin = 36f
        val tableW = PAGE_WIDTH - 2 * margin
        val borderPaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val headerPaint = Paint().apply {
            color = Color.rgb(13, 92, 75)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9.5f
        }

        var y = 40f
        canvas.drawRect(margin, y, margin + tableW, y + 26f, borderPaint)
        canvas.drawText("Study Report (Page $pageNumber) — ${record.studentName}", PAGE_WIDTH / 2f, y + 17f, headerPaint)
        y += 26f

        val wWeek = 54f
        val wFrom = 64f
        val wTo = 64f
        val wSig = 96f
        val wDesc = tableW - wWeek - wFrom - wTo - wSig
        val headerH = 30f

        canvas.drawRect(margin, y, margin + wWeek, y + headerH, borderPaint)
        canvas.drawRect(margin + wWeek, y, margin + wWeek + wFrom, y + headerH, borderPaint)
        canvas.drawRect(margin + wWeek + wFrom, y, margin + wWeek + wFrom + wTo, y + headerH, borderPaint)
        canvas.drawRect(margin + wWeek + wFrom + wTo, y, margin + wWeek + wFrom + wTo + wDesc, y + headerH, borderPaint)
        canvas.drawRect(margin + wWeek + wFrom + wTo + wDesc, y, margin + tableW, y + headerH, borderPaint)

        canvas.drawText("Week", margin + 10f, y + 19f, labelPaint)
        canvas.drawText("From", margin + wWeek + 14f, y + 19f, labelPaint)
        canvas.drawText("To", margin + wWeek + wFrom + 20f, y + 19f, labelPaint)
        canvas.drawText("Description & Period Utilization", margin + wWeek + wFrom + wTo + 10f, y + 19f, labelPaint)
        canvas.drawText("Auth Signature", margin + wWeek + wFrom + wTo + wDesc + 8f, y + 19f, labelPaint)
        y += headerH

        val rowH = 76f
        val byWeek = reports.associateBy { it.weekNumber.toInt() }
        for (week in weekRange) {
            val r = byWeek[week]
            canvas.drawRect(margin, y, margin + wWeek, y + rowH, borderPaint)
            canvas.drawRect(margin + wWeek, y, margin + wWeek + wFrom, y + rowH, borderPaint)
            canvas.drawRect(margin + wWeek + wFrom, y, margin + wWeek + wFrom + wTo, y + rowH, borderPaint)
            canvas.drawRect(margin + wWeek + wFrom + wTo, y, margin + wWeek + wFrom + wTo + wDesc, y + rowH, borderPaint)
            canvas.drawRect(margin + wWeek + wFrom + wTo + wDesc, y, margin + tableW, y + rowH, borderPaint)

            canvas.drawText("Week $week", margin + 8f, y + 40f, labelPaint)
            if (r != null) {
                canvas.drawText(r.dateFrom.take(10), margin + wWeek + 4f, y + 36f, bodyPaint)
                canvas.drawText(r.dateTo.take(10), margin + wWeek + wFrom + 4f, y + 36f, bodyPaint)

                val utilLine = "Periods: ${r.utilizedPeriods}/${r.allocatedPeriods} (${r.utilizationPercent}%) • Focus ${r.focusRating}/5"
                canvas.drawText(utilLine, margin + wWeek + wFrom + wTo + 6f, y + 16f, labelPaint)

                val descX = margin + wWeek + wFrom + wTo + 6f
                drawWrappedText(canvas, r.description, descX, y + 30f, wDesc - 12f, 12f, 3, bodyPaint)

                val sigX = margin + wWeek + wFrom + wTo + wDesc + 6f
                canvas.drawText(r.verificationStatus, sigX, y + 28f, labelPaint)
                canvas.drawText(r.authorizedSignature.take(16), sigX, y + 44f, bodyPaint)
            }
            y += rowH
        }
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float,
        maxLines: Int,
        paint: Paint
    ) {
        val words = text.split(Regex("\\s+"))
        var line = StringBuilder()
        var y = startY
        var lineCount = 0
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) <= maxWidth) {
                line = StringBuilder(test)
            } else {
                canvas.drawText(line.toString(), x, y, paint)
                lineCount++
                if (lineCount >= maxLines) return
                y += lineHeight
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty() && lineCount < maxLines) {
            canvas.drawText(line.toString(), x, y, paint)
        }
    }
}
