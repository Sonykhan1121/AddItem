package com.example.additem

data class Product(
    val id:String,
    val name:String,
    val category: String,
    val price:Float,
    val offerpercentage:Float?=null,
    val description:String? =null,
    val colors:List<Int>? = null,
    val sizes:List<String>?= null,
    val images:List<String>

)
