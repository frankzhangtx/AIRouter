package com.example.cctest.feature.personalinfo.data

class PersonalInfoRepository {
    private val records: List<PersonalInfoRecord> = buildMockRecords()

    fun getRecords(): List<PersonalInfoRecord> = records

    fun getRecordById(recordId: String?): PersonalInfoRecord? {
        return records.firstOrNull { it.recordId == recordId }
    }

    private fun buildMockRecords(): List<PersonalInfoRecord> {
        val names = listOf(
            "陈晓宇", "张雨桐", "李晨曦", "吴佳宁", "孙浩然",
            "徐诗涵", "林若溪", "赵子轩", "周梦瑶", "郭嘉铭",
            "韩书宁", "唐雅雯", "宋知远", "冯可心", "胡景澄",
            "顾星野", "许安然", "邵雨辰", "潘若琳", "曹翊凡",
            "谭嘉禾", "程可欣", "郑语桐", "罗景行", "高心语",
            "梁宇哲", "何佳悦", "崔子航", "杜清妍", "张子墨"
        )
        val cities = listOf(
            "上海", "北京", "深圳", "杭州", "成都",
            "南京", "武汉", "苏州", "厦门", "广州"
        )
        val occupations = listOf(
            "设计师", "工程师", "教师", "产品经理", "医生",
            "律师", "分析师", "顾问", "作家", "建筑师"
        )
        val hobbies = listOf(
            "摄影、徒步", "跑步、烘焙", "阅读、旅行", "游泳、露营", "瑜伽、插花",
            "吉他、电影", "骑行、手账", "咖啡、园艺", "桌游、绘画", "羽毛球、写作"
        )
        val companyPrefixes = listOf(
            "星河", "远景", "启明", "云岚", "澄海",
            "光年", "北辰", "新岸", "曜石", "晨风"
        )
        val emergencyContacts = listOf(
            "王欣", "李可", "周凡", "张扬", "赵宁",
            "孙悦", "陈安", "许飞", "杨曦", "吴晨"
        )

        return names.mapIndexed { index, name ->
            val city = cities[index % cities.size]
            val occupation = occupations[index % occupations.size]
            val hobby = hobbies[index % hobbies.size]
            val company = "${companyPrefixes[index % companyPrefixes.size]}科技"
            val phoneSuffix = 1000 + index
            PersonalInfoRecord(
                recordId = "record-${index + 1}",
                name = name,
                age = 24 + (index % 8),
                occupation = occupation,
                city = city,
                phone = "1380000$phoneSuffix",
                email = "record${index + 1}@example.com",
                address = "${city}市星河路${10 + index}号",
                hobbies = hobby,
                company = company,
                emergencyContact = emergencyContacts[index % emergencyContacts.size]
            )
        }
    }
}
