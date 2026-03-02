package com.example.myapplication.model

data class PermissionsResponse(
    val actions: List<ActionGroup>
)

data class ActionGroup(
    val label: String,
    val items: List<ActionItem>
)

data class ActionItem(
    val label: String,
    val payload: String,
    val parameters: Map<String, ActionParameter>?
)

data class ActionParameter(
    val type: String,
    val label: String?,
    val value: String?,
    val required: String?,
    val min: Int?,
    val max: Int?
)
