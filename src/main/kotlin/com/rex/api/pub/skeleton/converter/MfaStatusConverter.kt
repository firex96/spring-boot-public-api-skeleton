package com.rex.api.pub.skeleton.converter

import com.rex.api.pub.skeleton.enums.StringEnumUtil
import com.rex.api.pub.skeleton.enums.auth.MfaStatus

class MfaStatusConverter : AbstractStringEnumConverter<MfaStatus>(nullable = false, fromValue = StringEnumUtil::fromValue)