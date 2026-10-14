package com.rex.api.pub.skeleton.converter

import com.rex.api.pub.skeleton.enums.ProductStatus
import com.rex.api.pub.skeleton.enums.StringEnumUtil

class ProductStatusConverter : AbstractStringEnumConverter<ProductStatus>(nullable = false, fromValue = StringEnumUtil::fromValue)