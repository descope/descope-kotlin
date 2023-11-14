package com.descope.session

import com.descope.internal.others.toMap
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class JsonTest {

    private val jsonString = """
        {
            "obj": {
                "obj": {
                    "string": "yes",
                    "boolean": false,
                    "integer": 100,
                    "float": 10.5,
                    "array": [
                        {
                            "string": "yes",
                            "boolean": false,
                            "integer": 100,
                            "float": 10.5
                        },
                        [
                            "yes",
                            false,
                            100,
                            10.5
                        ],
                        "yes",
                        false,
                        100,
                        10.5
                    ]
                },
                "array": [
                    {
                        "string": "yes",
                        "boolean": false,
                        "integer": 100,
                        "float": 10.5
                    },
                    [
                        "yes",
                        false,
                        100,
                        10.5
                    ],
                    "yes",
                    false,
                    100,
                    10.5
                ],
                "string": "yes",
                "boolean": false,
                "integer": 100,
                "float": 10.5
            }
        }
    """.trimIndent()

    @Test
    fun json_toMap() {
        val jsonObject = JSONObject(jsonString)
        val map = jsonObject.toMap()
        assertEquals(1, map.size)
        // obj
        val obj = map["obj"] as Map<*, *>
        assertEquals(6, obj.size)
        validateMap(obj)
        
        // obj / obj
        val objObj = obj["obj"] as Map<*, *>
        validateMap(objObj)
        // obj / obj / array
        val objObjArray = objObj["array"] as List<*>
        validateList(objObjArray)
        //  obj / obj / array / 0
        val objObjArrayObj = objObjArray.first() as Map<*, *>
        validateMap(objObjArrayObj)
        // obj / obj / array / 1
        val objObjArrayArray = objObjArray[1] as List<*>
        validateList(objObjArrayArray)
        
        
        // obj / array
        val objArray = obj["array"] as List<*>
        validateList(objArray)
        // obj / array / 0
        val objArrayArray = objArray.first() as Map<*, *>
        validateMap(objArrayArray)
        // obj / array / 1
        val objArrayArrayArray = objArray[1] as List<*>
        validateList(objArrayArrayArray)
    }
    
    private fun validateMap(map: Map<*, *>) {
        assertTrue(map["string"] == "yes")
        assertTrue(map["boolean"] == false)
        assertTrue(map["integer"] == 100)
        assertTrue(map["float"] == BigDecimal(10.5))
    }

    private fun validateList(list: List<*>) {
        assertTrue(4 <= list.size)
        val found = mutableMapOf<String, Boolean>()
        list.forEach { 
            when(it) {
                is String -> found["String"] = it == "yes"
                is Boolean -> found["Boolean"] = it == false
                is Int -> found["Integer"] = it == 100
                is BigDecimal -> found["Float"] = it == BigDecimal(10.5)
            }
        }
        assertEquals(4, found.size)
        assertTrue(found.values.reduce { acc, b -> acc && b })
    }
}