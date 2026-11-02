package com.sixmonths.app.core.util

import java.util.UUID

/** All entity ids are random UUID strings generated on the device. */
fun newId(): String = UUID.randomUUID().toString()
