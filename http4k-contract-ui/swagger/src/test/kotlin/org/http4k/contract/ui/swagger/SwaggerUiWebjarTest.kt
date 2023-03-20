package org.http4k.contract.ui.swagger

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.testing.ApprovalTest
import org.http4k.testing.Approver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ApprovalTest::class)
class SwaggerUiWebjarTest {

    @Test
    fun `can serve swagger ui index`(approver: Approver) {
        val handler = swaggerUiWebjar {
            url = "spec"
            pageTitle = "Cat Shelter"
            displayOperationId = true
            requestSnippetsEnabled = true
        }
        approver.assertApproved(handler(Request(GET, "")))
    }

    @Test
    fun `can serve custom swagger ui initializer`(approver: Approver) {
        val handler = swaggerUiWebjar {
            url = "spec"
            pageTitle = "Cat Shelter"
            displayOperationId = true
            requestSnippetsEnabled = true
        }
        approver.assertApproved(handler(Request(GET, "swagger-initializer.js")))
    }

    @Test
    fun `can serve swagger oauth2 redirect`(approver: Approver) {
        val handler = swaggerUiWebjar {
            url = "spec"
            pageTitle = "Cat Shelter"
            displayOperationId = true
            requestSnippetsEnabled = true
        }
        approver.assertApproved(handler(Request(GET, "oauth2-redirect.html")))
    }
}
