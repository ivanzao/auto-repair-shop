package br.com.soat.shared.model

data class Page<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
) {
    companion object {
        fun <T> of(content: List<T>, page: Int, size: Int, totalElements: Long): Page<T> {
            val totalPages = if (totalElements == 0L) 0 else ((totalElements - 1) / size + 1).toInt()
            return Page(content, page, size, totalElements, totalPages)
        }
    }
}
