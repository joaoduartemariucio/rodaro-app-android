package br.com.joaoduartemariucio.rodaro.fakes

import br.com.joaoduartemariucio.rodaro.domain.reports.services.ReportFileStore
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.ReportDocument
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.ReportRenderer

class InMemoryReportFileStore(
    var failure: Exception? = null,
) : ReportFileStore {
    val written = mutableMapOf<String, ByteArray>()
    val shared = mutableMapOf<String, ByteArray>()

    override suspend fun write(
        location: String,
        bytes: ByteArray,
    ) {
        failure?.let { throw it }
        written[location] = bytes
    }

    override suspend fun shareable(
        fileName: String,
        bytes: ByteArray,
    ): String {
        failure?.let { throw it }
        shared[fileName] = bytes
        return "content://test/$fileName"
    }
}

class FakeReportRenderer(
    var failure: Exception? = null,
) : ReportRenderer {
    val documents = mutableListOf<ReportDocument>()
    val labels = mutableListOf<String>()

    override suspend fun render(
        document: ReportDocument,
        pageLabel: (Int, Int) -> String,
    ): ByteArray {
        failure?.let { throw it }
        documents += document
        labels += pageLabel(1, 2)
        return document.text.toByteArray()
    }
}
