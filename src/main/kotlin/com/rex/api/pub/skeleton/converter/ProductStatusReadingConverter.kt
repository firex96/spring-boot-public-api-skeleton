package com.rex.api.pub.skeleton.converter

import com.rex.api.pub.skeleton.enums.ProductStatus
import com.rex.api.pub.skeleton.enums.StringEnumUtil

class ProductStatusReadingConverter : AbstractStringEnumReadingConverter<ProductStatus>(StringEnumUtil::fromValue)