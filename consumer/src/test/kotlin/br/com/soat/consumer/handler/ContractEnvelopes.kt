package br.com.soat.consumer.handler

const val DIAGNOSE_FINISHED_ENVELOPE = """
{
  "eventId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "eventType": "DiagnoseFinished",
  "eventVersion": 1,
  "occurredAt": "2026-07-25T18:00:00Z",
  "payload": {
    "orderId": "11111111-1111-1111-1111-111111111111",
    "reservationId": "44444444-4444-4444-4444-444444444444",
    "diagnosedBy": {
      "id": "00000000-0000-0000-0000-000000000003",
      "document": "12345678909"
    },
    "customer": {
      "name": "Maria Silva",
      "email": "maria@exemplo.com"
    },
    "services": [
      { "id": "55555555-5555-5555-5555-555555555555", "name": "Troca de oleo", "price": 100.00 }
    ],
    "supplies": [
      { "id": "66666666-6666-6666-6666-666666666666", "name": "Filtro de oleo", "quantity": 2, "unitPrice": 30.00 }
    ],
    "totalAmount": 160.00
  }
}
"""

const val EXECUTION_STARTED_ENVELOPE = """
{
  "eventId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
  "eventType": "ExecutionStarted",
  "eventVersion": 1,
  "occurredAt": "2026-07-25T18:00:00Z",
  "payload": {
    "orderId": "11111111-1111-1111-1111-111111111111"
  }
}
"""

const val SUPPLIES_UNAVAILABLE_ENVELOPE = """
{
  "eventId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "eventType": "SuppliesUnavailable",
  "eventVersion": 1,
  "occurredAt": "2026-07-25T18:00:00Z",
  "payload": {
    "orderId": "11111111-1111-1111-1111-111111111111",
    "missingSupplies": [
      {
        "supplyId": "66666666-6666-6666-6666-666666666666",
        "name": "Filtro de oleo",
        "requested": 4,
        "available": 1
      }
    ]
  }
}
"""
