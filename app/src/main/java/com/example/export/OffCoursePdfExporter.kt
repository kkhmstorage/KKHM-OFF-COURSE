package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.model.CIC_DAYS
import com.example.data.model.CIC_PERIODS
import com.example.data.model.GuideType
import com.example.data.model.OffCourseRecord
import com.example.data.model.PeriodLogEntry
import com.example.data.model.WeekVerificationStatus
import com.example.data.model.WeeklyStudyReport
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OffCoursePdfExporter {

    private const val PAGE_WIDTH = 595 // A4 Portrait points
    private const val PAGE_HEIGHT = 842
    private const val LANDSCAPE_WIDTH = 842 // A4 Landscape points
    private const val LANDSCAPE_HEIGHT = 595

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

    fun buildConsolidatedWeeklyReportPdfFile(
        context: Context,
        academicYear: String,
        semester: String,
        weekFilter: Int?,
        records: List<OffCourseRecord>,
        reportsByRecordId: Map<String, List<WeeklyStudyReport>>
    ): Result<File> {
        return try {
            val document = PdfDocument()
            val margin = 32f
            val tableW = LANDSCAPE_WIDTH - 2 * margin
            val headerBgPaint = Paint().apply {
                color = Color.rgb(232, 245, 241)
                style = Paint.Style.FILL
            }
            val borderPaint = Paint().apply {
                color = Color.rgb(90, 105, 100)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            val titlePaint = Paint().apply {
                color = Color.rgb(13, 92, 75)
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 9.5f
            }
            val colHeaderPaint = Paint().apply {
                color = Color.rgb(13, 92, 75)
                textSize = 8.8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val cellBoldPaint = Paint().apply {
                color = Color.BLACK
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val cellPaint = Paint().apply {
                color = Color.rgb(32, 36, 35)
                textSize = 8.2f
            }

            data class ConsolidatedRow(
                val studentLabel: String,
                val studentSub: String,
                val subjectLabel: String,
                val exemptedLabel: String,
                val guideLabel: String,
                val weekLabel: String,
                val periodsLabel: String,
                val workSummary: String,
                val statusLabel: String
            )

            val flatRows = mutableListOf<ConsolidatedRow>()
            val sortedRecords = records.sortedWith(compareBy<OffCourseRecord> { it.studentName.lowercase() }.thenBy { it.subjectTitle.lowercase() })
            for (rec in sortedRecords) {
                val reports = reportsByRecordId[rec.id].orEmpty()
                    .filter { weekFilter == null || it.weekNumber.toInt() == weekFilter }
                    .sortedBy { it.weekNumber }
                val guideTypeShort = if (rec.guideType == GuideType.EXTERNAL.name) "External" else "Faculty"
                if (reports.isEmpty()) {
                    flatRows += ConsolidatedRow(
                        studentLabel = rec.studentName,
                        studentSub = "${rec.cicAdNo} • ${rec.classDepartment}",
                        subjectLabel = rec.subjectTitle,
                        exemptedLabel = rec.exemptedSubject.ifBlank { "-" },
                        guideLabel = "${rec.guideName} ($guideTypeShort)",
                        weekLabel = weekFilter?.let { "W$it (Not Logged)" } ?: "No Weeks Yet",
                        periodsLabel = "0/${rec.periodsPerWeek}",
                        workSummary = "No weekly activity report submitted yet.",
                        statusLabel = "Pending"
                    )
                } else {
                    for (rep in reports) {
                        val decoded = rep.periodLogs.mapNotNull { PeriodLogEntry.decode(it) }
                        val periodSummary = decoded.joinToString(" | ") { entry ->
                            val act = if (entry.activityType.isNotBlank()) "[${entry.activityType}] " else ""
                            "${entry.day.take(3)} P${entry.period} (${entry.effectiveness.take(3)}, ${entry.minutes}m): $act${entry.note.ifBlank { "-" }}"
                        }.ifBlank { rep.description.ifBlank { "Logged off-course study periods" } }
                        flatRows += ConsolidatedRow(
                            studentLabel = rec.studentName,
                            studentSub = "${rec.cicAdNo} • ${rec.classDepartment}",
                            subjectLabel = rec.subjectTitle,
                            exemptedLabel = rec.exemptedSubject.ifBlank { "-" },
                            guideLabel = "${rec.guideName} ($guideTypeShort)",
                            weekLabel = "W${rep.weekNumber}\n${rep.dateFrom.take(10)}",
                            periodsLabel = "${rep.utilizedPeriods}/${rep.allocatedPeriods} (${rep.utilizationPercent}%)",
                            workSummary = periodSummary,
                            statusLabel = "${WeekVerificationStatus.fromRaw(rep.verificationStatus).label}${if (rep.authorizedSignature.isNotBlank()) "\n${rep.authorizedSignature}" else ""}"
                        )
                    }
                }
            }

            if (flatRows.isEmpty()) {
                flatRows += ConsolidatedRow(
                    studentLabel = "No Students",
                    studentSub = "-",
                    subjectLabel = "No Off-Course records found for filter",
                    exemptedLabel = "-",
                    guideLabel = "-",
                    weekLabel = "-",
                    periodsLabel = "-",
                    workSummary = "Enroll students in this academic term to print weekly activity reports.",
                    statusLabel = "-"
                )
            }

            // Column widths summing to tableW (778pt)
            val cStudent = 105f
            val cSubject = 118f
            val cExempt = 90f
            val cGuide = 88f
            val cWeek = 58f
            val cPeriods = 56f
            val cWork = 195f
            val cStatus = tableW - (cStudent + cSubject + cExempt + cGuide + cWeek + cPeriods + cWork)
            val cols = listOf(cStudent, cSubject, cExempt, cGuide, cWeek, cPeriods, cWork, cStatus)
            val headers = listOf("Student & CIC", "Off-Course Subject", "Exempted Subject", "Guide", "Week", "Periods", "Period-by-Period Activity", "Approval")

            val termLabel = "${if (academicYear == "ALL") "All Years" else academicYear} • ${if (semester == "ALL") "All Semesters" else semester}"
            val weekTitle = weekFilter?.let { "Week $it Report" } ?: "All Logged Weeks"
            val printedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())

            var pageNum = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT, pageNum).create())
            var canvas = page.canvas

            fun drawPageHeader(c: Canvas, pNum: Int): Float {
                var y = 34f
                c.drawText("KKHM ISLAMIC & ARTS COLLEGE — CONSOLIDATED OFF-COURSE WEEKLY REPORT", margin, y, titlePaint)
                y += 15f
                c.drawText("Term: $termLabel   |   Scope: $weekTitle   |   Students: ${records.size}   |   Generated: $printedDate   |   Page $pNum", margin, y, subtitlePaint)
                y += 12f
                val hHeight = 22f
                c.drawRect(margin, y, margin + tableW, y + hHeight, headerBgPaint)
                var x = margin
                cols.forEachIndexed { i, w ->
                    c.drawRect(x, y, x + w, y + hHeight, borderPaint)
                    c.drawText(headers[i], x + 4f, y + 14f, colHeaderPaint)
                    x += w
                }
                return y + hHeight
            }

            var y = drawPageHeader(canvas, pageNum)
            val rowHeight = 52f
            val maxBottom = LANDSCAPE_HEIGHT - 28f

            for (row in flatRows) {
                if (y + rowHeight > maxBottom) {
                    document.finishPage(page)
                    pageNum++
                    page = document.startPage(PdfDocument.PageInfo.Builder(LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT, pageNum).create())
                    canvas = page.canvas
                    y = drawPageHeader(canvas, pageNum)
                }

                var x = margin
                cols.forEach { w ->
                    canvas.drawRect(x, y, x + w, y + rowHeight, borderPaint)
                    x += w
                }

                var cx = margin
                drawWrappedText(canvas, row.studentLabel, cx + 4f, y + 12f, cStudent - 8f, 10f, 2, cellBoldPaint)
                drawWrappedText(canvas, row.studentSub, cx + 4f, y + 34f, cStudent - 8f, 9.5f, 2, cellPaint)
                cx += cStudent

                drawWrappedText(canvas, row.subjectLabel, cx + 4f, y + 12f, cSubject - 8f, 10f, 4, cellPaint)
                cx += cSubject

                drawWrappedText(canvas, row.exemptedLabel, cx + 4f, y + 12f, cExempt - 8f, 10f, 4, cellPaint)
                cx += cExempt

                drawWrappedText(canvas, row.guideLabel, cx + 4f, y + 12f, cGuide - 8f, 10f, 4, cellPaint)
                cx += cGuide

                drawWrappedText(canvas, row.weekLabel.replace("\n", " "), cx + 4f, y + 12f, cWeek - 8f, 10f, 4, cellBoldPaint)
                cx += cWeek

                drawWrappedText(canvas, row.periodsLabel, cx + 4f, y + 12f, cPeriods - 8f, 10f, 4, cellBoldPaint)
                cx += cPeriods

                drawWrappedText(canvas, row.workSummary, cx + 4f, y + 11f, cWork - 8f, 9.5f, 4, cellPaint)
                cx += cWork

                drawWrappedText(canvas, row.statusLabel.replace("\n", " • "), cx + 4f, y + 12f, cStatus - 8f, 9.5f, 4, cellBoldPaint)

                y += rowHeight
            }

            document.finishPage(page)

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeTerm = "${academicYear}_${semester}".replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val wkSuffix = weekFilter?.let { "_W$it" } ?: "_AllWeeks"
            val pdfFile = File(exportDir, "KKHM_Weekly_Consolidated_${safeTerm}${wkSuffix}.pdf")
            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
            document.close()
            Result.success(pdfFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun printConsolidatedWeeklyReport(
        context: Context,
        academicYear: String,
        semester: String,
        weekFilter: Int?,
        records: List<OffCourseRecord>,
        reportsByRecordId: Map<String, List<WeeklyStudyReport>>
    ): Result<Unit> {
        val fileResult = buildConsolidatedWeeklyReportPdfFile(
            context = context,
            academicYear = academicYear,
            semester = semester,
            weekFilter = weekFilter,
            records = records,
            reportsByRecordId = reportsByRecordId
        )
        val pdfFile = fileResult.getOrElse { return Result.failure(it) }
        return try {
            val printManager = context.getSystemService(PrintManager::class.java)
                ?: return Result.failure(IllegalStateException("Android Print service unavailable on this device."))
            val jobName = "KKHM Off-Course Weekly Report ($academicYear $semester)"
            val adapter = PdfFilePrintAdapter(pdfFile, pdfFile.name)
            val attrs = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()
            printManager.print(jobName, adapter, attrs)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareConsolidatedWeeklyReportPdf(
        context: Context,
        academicYear: String,
        semester: String,
        weekFilter: Int?,
        records: List<OffCourseRecord>,
        reportsByRecordId: Map<String, List<WeeklyStudyReport>>
    ): Result<Unit> {
        val fileResult = buildConsolidatedWeeklyReportPdfFile(
            context = context,
            academicYear = academicYear,
            semester = semester,
            weekFilter = weekFilter,
            records = records,
            reportsByRecordId = reportsByRecordId
        )
        val pdfFile = fileResult.getOrElse { return Result.failure(it) }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "KKHM Consolidated Off-Course Weekly Report ($academicYear $semester)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Consolidated Weekly Report PDF")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class PdfFilePrintAdapter(
        private val file: File,
        private val documentName: String
    ) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(documentName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead = input.read(buffer)
                        while (bytesRead >= 0) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback.onWriteCancelled()
                                return
                            }
                            output.write(buffer, 0, bytesRead)
                            bytesRead = input.read(buffer)
                        }
                        output.flush()
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.localizedMessage ?: "Failed to write PDF to printer")
            }
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

        canvas.drawRect(margin, margin, PAGE_WIDTH - margin, 440f, borderPaint)
        canvas.drawText("KKHM ISLAMIC & ARTS COLLEGE", PAGE_WIDTH / 2f, 155f, collegePaint)
        canvas.drawText("Affiliated to Coordination of Islamic Colleges (CIC)", PAGE_WIDTH / 2f, 174f, subPaint)
        canvas.drawText("OFF COURSE RECORD", PAGE_WIDTH / 2f, 240f, titlePaint)
        canvas.drawText(record.subjectTitle.take(46), PAGE_WIDTH / 2f, 276f, Paint(subPaint).apply {
            textSize = 14f
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        if (record.exemptedSubject.isNotBlank()) {
            canvas.drawText(
                "Exempted / Dropped Subject: ${record.exemptedSubject.take(48)}",
                PAGE_WIDTH / 2f,
                302f,
                Paint(subPaint).apply {
                    textSize = 11.5f
                    color = Color.rgb(13, 92, 75)
                }
            )
        }
        canvas.drawText(
            "Utilization: ${record.utilizationPercent}% (${record.totalUtilizedPeriods}/${record.totalAllocatedPeriods} Periods)",
            PAGE_WIDTH / 2f,
            330f,
            subPaint
        )

        var y = 466f
        val rowH = 34f
        val col1W = 160f
        val tableW = PAGE_WIDTH - 2 * margin

        fun drawRow(label: String, value: String) {
            canvas.drawRect(margin, y, margin + col1W, y + rowH, borderPaint)
            canvas.drawRect(margin + col1W, y, margin + tableW, y + rowH, borderPaint)
            canvas.drawText(label, margin + 8f, y + 21f, labelPaint)
            canvas.drawText(value.take(56), margin + col1W + 8f, y + 21f, valuePaint)
            y += rowH
        }

        drawRow("Name of Student", record.studentName)
        drawRow("CIC Ad No / Clg Ad No", "${record.cicAdNo}   |   Clg: ${record.clgAdNo.ifBlank { "-" }}")
        drawRow("Class / Department", record.classDepartment)
        drawRow("Academic Year / Sem", "${record.academicYear}   |   Semester: ${record.semester}")
        drawRow("Exempted Subject", record.exemptedSubject.ifBlank { "-" })
        drawRow("Programme Stream", record.stream)
        drawRow("College Affl. & Name", record.collegeAfflName)

        y += 32f
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

        var y = 44f
        canvas.drawRect(margin, y, margin + tableW, y + 26f, borderPaint)
        canvas.drawText("Subject Details", PAGE_WIDTH / 2f, y + 17f, headerPaint)
        y += 26f

        fun drawDetailRow(label: String, value: String, height: Float = 28f) {
            canvas.drawRect(margin, y, margin + 155f, y + height, borderPaint)
            canvas.drawRect(margin + 155f, y, margin + tableW, y + height, borderPaint)
            canvas.drawText(label, margin + 8f, y + 18f, labelPaint)
            canvas.drawText(value.take(60), margin + 163f, y + 18f, valuePaint)
            y += height
        }

        val guideTypeLabel = if (record.guideType.isNotBlank()) {
            " (${GuideType.fromRaw(record.guideType).label})"
        } else ""

        drawDetailRow("Off-Course Subject", record.subjectTitle)
        drawDetailRow("Exempted Subject", record.exemptedSubject.ifBlank { "-" })
        drawDetailRow("Proposal App No / Credit", "${record.proposalAppNo.ifBlank { "-" }}   |   Credit: ${record.credit}")
        drawDetailRow("Name of Guide", "${record.guideName}$guideTypeLabel")
        drawDetailRow("Phone / WhatsApp", "${record.guidePhone.ifBlank { "-" }}   |   WA: ${record.guideWhatsapp.ifBlank { "-" }}")
        drawDetailRow("Reference", record.referenceBooks.ifBlank { "-" }, height = 48f)

        y += 24f
        canvas.drawRect(margin, y, margin + tableW, y + 26f, borderPaint)
        canvas.drawText("Timetable", PAGE_WIDTH / 2f, y + 17f, headerPaint)
        y += 26f

        val dayColW = 100f
        val periodColW = (tableW - dayColW) / CIC_PERIODS.size
        val cellH = 29f

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

        canvas.drawRect(margin, y, margin + tableW - 100f, y + 28f, borderPaint)
        canvas.drawRect(margin + tableW - 100f, y, margin + tableW, y + 28f, borderPaint)
        canvas.drawText("Period per Week", PAGE_WIDTH / 2f - 40f, y + 18f, labelPaint)
        canvas.drawText(record.periodsPerWeek.toString(), margin + tableW - 55f, y + 18f, labelPaint)

        y += 82f
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
                val decodedPeriods = r.periodLogs.mapNotNull { PeriodLogEntry.decode(it) }
                val periodNoteSummary = decodedPeriods
                    .filter { it.note.isNotBlank() }
                    .joinToString("; ") {
                        val act = if (it.activityType.isNotBlank()) "[${it.activityType}] " else ""
                        "${it.day.take(3)} P${it.period}(${it.effectiveness.take(3)}): $act${it.note}"
                    }
                val combinedText = buildString {
                    if (r.description.isNotBlank()) append(r.description.trim())
                    if (periodNoteSummary.isNotBlank() && periodNoteSummary != r.description.trim()) {
                        if (isNotEmpty()) append(" | ")
                        append(periodNoteSummary)
                    }
                }.ifBlank { "Completed off-course study periods" }
                drawWrappedText(canvas, combinedText, descX, y + 30f, wDesc - 12f, 11f, 3, bodyPaint)

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
