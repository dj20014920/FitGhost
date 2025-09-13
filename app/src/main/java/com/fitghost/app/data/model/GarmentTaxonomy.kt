package com.fitghost.app.data.model

/**
 * GarmentTaxonomy
 *
 * 목적
 * - 옷의 상위 카테고리(Category)와 하위 서브카테고리(Subcategory) 분류를 표준화한다.
 * - 한국어/영어 별칭(aliases)을 통해 검색/필터/태그 자동화에 활용한다.
 * - 기존 DB 스키마(type: "T"/"B"/"O")와의 호환을 돕는 헬퍼를 제공한다.
 * - Try-On 파트("TOP"/"BOTTOM") 매핑을 제공한다.
 *
 * 원칙
 * - KISS/DRY/YAGNI: 최소한의 구조로 재사용성을 극대화.
 * - 중복 금지: 모든 분류/별칭은 본 모듈을 단일 소스로 사용.
 *
 * 주의
 * - 실제 제품에서는 분류 정의가 더 방대해질 수 있다. 필요 시 서브카테고리/별칭만 확장하면 되도록 설계했다.
 */
object GarmentTaxonomy {

    /** 상위 카테고리 */
    enum class Category {
        TOP, // 상의
        BOTTOM, // 하의
        OUTER, // 아우터(겉옷)
        DRESS, // 원피스
        SHOES, // 신발
        SOCKS, // 양말
        UNDERWEAR, // 속옷
        ACCESSORY, // 액세서리(벨트/시계/가방/목걸이 등)
        HEADWEAR // 모자/비니 등
    }

    /**
     * 서브카테고리
     *
     * @property category 상위 카테고리
     * @property aliases 한국어/영어/약칭 포함된 검색 토큰
     * @property warmthBias 상대적 보온(+는 더 따뜻함), -2..+3 범위 권장
     */
    enum class Subcategory(
            val category: Category,
            val aliases: Set<String>,
            val warmthBias: Int = 0
    ) {
        // TOP — 상의
        TOP_SHORT_SLEEVE_TSHIRT(
                Category.TOP,
                setOf("반팔", "티셔츠", "티", "tshirt", "t-shirt", "tee", "shortsleeve"),
                warmthBias = 0
        ),
        TOP_LONG_SLEEVE_TSHIRT(
                Category.TOP,
                setOf("긴팔", "롱슬리브", "longsleeve", "ls tee", "long sleeve t-shirt"),
                warmthBias = 1
        ),
        TOP_SHIRT(Category.TOP, setOf("셔츠", "와이셔츠", "shirt", "dress shirt"), warmthBias = 1),
        TOP_BLOUSE(Category.TOP, setOf("블라우스", "blouse"), warmthBias = 1),
        TOP_POLO(Category.TOP, setOf("폴로", "카라티", "polo", "polo shirt"), warmthBias = 0),
        TOP_HOODIE(Category.TOP, setOf("후드", "후드티", "후디", "hoodie", "hooded"), warmthBias = 2),
        TOP_SWEATSHIRT(
                Category.TOP,
                setOf("맨투맨", "스웨트셔츠", "sweatshirt", "crewneck"),
                warmthBias = 2
        ),
        TOP_KNIT(Category.TOP, setOf("니트", "knit", "knitted"), warmthBias = 2),
        TOP_SWEATER(Category.TOP, setOf("스웨터", "sweater", "wool sweater"), warmthBias = 2),
        TOP_VEST(Category.TOP, setOf("조끼", "베스트", "vest"), warmthBias = 1),
        TOP_CARDIGAN(Category.TOP, setOf("가디건(상의)", "가디건 상의", "cardigan top"), warmthBias = 2),

        // OUTER — 겉옷
        OUTER_JACKET(
                Category.OUTER,
                setOf("재킷", "자켓", "jacket", "denim jacket", "leather jacket"),
                warmthBias = 2
        ),
        OUTER_JUMPER(Category.OUTER, setOf("점퍼", "jumper", "bomber", "ma-1"), warmthBias = 2),
        OUTER_WIND_BREAKER(
                Category.OUTER,
                setOf("바람막이", "윈드브레이커", "windbreaker", "wind breaker"),
                warmthBias = 1
        ),
        OUTER_COAT(Category.OUTER, setOf("코트", "coat", "wool coat"), warmthBias = 3),
        OUTER_TRENCH(
                Category.OUTER,
                setOf("트렌치코트", "트렌치", "trench", "trench coat"),
                warmthBias = 2
        ),
        OUTER_PARKA(Category.OUTER, setOf("파카", "parka", "anorak"), warmthBias = 3),
        OUTER_BLAZER(Category.OUTER, setOf("블레이저", "blazer"), warmthBias = 2),
        OUTER_CARDIGAN(Category.OUTER, setOf("가디건", "cardigan"), warmthBias = 2),

        // BOTTOM — 하의
        BOTTOM_JEANS(Category.BOTTOM, setOf("청바지", "데님", "진", "jeans", "denim"), warmthBias = 1),
        BOTTOM_SLACKS(Category.BOTTOM, setOf("슬랙스", "슬렉스", "slacks"), warmthBias = 1),
        BOTTOM_TROUSERS(Category.BOTTOM, setOf("팬츠", "바지", "trousers", "pants"), warmthBias = 1),
        BOTTOM_JOGGER(
                Category.BOTTOM,
                setOf("조거", "조거팬츠", "jogger", "jogger pants"),
                warmthBias = 1
        ),
        BOTTOM_LEGGINGS(Category.BOTTOM, setOf("레깅스", "leggings", "tights"), warmthBias = 0),
        BOTTOM_SHORTS(Category.BOTTOM, setOf("반바지", "쇼츠", "shorts"), warmthBias = 0),
        BOTTOM_SKIRT(Category.BOTTOM, setOf("치마", "스커트", "skirt"), warmthBias = 0),

        // DRESS — 원피스
        DRESS_GENERIC(Category.DRESS, setOf("원피스", "dress", "onepiece"), warmthBias = 2),

        // SHOES — 신발
        SHOES_SNEAKERS(Category.SHOES, setOf("운동화", "스니커즈", "sneakers", "sneaker"), warmthBias = 0),
        SHOES_DRESS(
                Category.SHOES,
                setOf("구두", "dress shoes", "oxford", "derby", "brogue"),
                warmthBias = 0
        ),
        SHOES_BOOTS(
                Category.SHOES,
                setOf("부츠", "boots", "chelsea", "chukka", "hiking boots"),
                warmthBias = 1
        ),
        SHOES_SANDALS(Category.SHOES, setOf("샌들", "sandals", "slides"), warmthBias = -1),
        SHOES_LOAFERS(Category.SHOES, setOf("로퍼", "loafers", "loafer"), warmthBias = 0),
        SHOES_HEELS(Category.SHOES, setOf("힐", "pumps", "heels", "high heels"), warmthBias = 0),

        // SOCKS — 양말
        SOCKS_GENERIC(Category.SOCKS, setOf("양말", "socks", "sock"), warmthBias = 0),

        // UNDERWEAR — 속옷
        UNDERWEAR_GENERIC(
                Category.UNDERWEAR,
                setOf("속옷", "언더웨어", "underwear", "bra", "panties"),
                warmthBias = 0
        ),

        // ACCESSORY — 액세서리
        ACC_BELT(Category.ACCESSORY, setOf("벨트", "belt"), warmthBias = 0),
        ACC_NECKLACE(Category.ACCESSORY, setOf("목걸이", "necklace", "chain"), warmthBias = 0),
        ACC_WATCH(Category.ACCESSORY, setOf("시계", "watch"), warmthBias = 0),
        ACC_BAG(
                Category.ACCESSORY,
                setOf("가방", "백", "bag", "handbag", "tote", "backpack"),
                warmthBias = 0
        ),

        // HEADWEAR — 모자
        HEADWEAR_CAP(
                Category.HEADWEAR,
                setOf("모자", "캡", "야구모자", "cap", "baseball cap"),
                warmthBias = 0
        ),
        HEADWEAR_BEANIE(Category.HEADWEAR, setOf("비니", "니트모", "beanie"), warmthBias = 1),
        HEADWEAR_HAT(
                Category.HEADWEAR,
                setOf("햇", "해트", "페도라", "버킷햇", "hat", "fedora", "bucket hat"),
                warmthBias = 0
        ),
    }

    // ============ Constants for legacy interop ============
    const val LEGACY_TYPE_TOP = "T"
    const val LEGACY_TYPE_BOTTOM = "B"
    const val LEGACY_TYPE_OUTER = "O"

    const val TRY_ON_PART_TOP = "TOP"
    const val TRY_ON_PART_BOTTOM = "BOTTOM"

    // ============ Indexes ============
    private val aliasIndex: Map<String, Subcategory> by lazy {
        buildMap {
            Subcategory.entries.forEach { sub ->
                sub.aliases.forEach { a -> put(normalize(a), sub) }
            }
        }
    }

    /** 전체 키워드(검색 추천용) */
    val allKeywords: Set<String> by lazy {
        Subcategory.entries
                .flatMap { it.aliases }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
    }

    // ============ Helpers ============

    /** 공통 토큰 정규화: 소문자 + 공백/하이픈 제거 */
    fun normalize(token: String): String = token.lowercase().replace("-", "").replace(" ", "")

    /**
     * 자유 텍스트에서 서브카테고리 추정.
     * - 입력 문자열을 공백/구분자 단위로 쪼개고, 각 토큰을 정규화하여 aliasIndex에서 탐색
     * - 우선 매칭(첫 매칭) 반환
     */
    fun matchSubcategoryFreeText(text: String): Subcategory? {
        if (text.isBlank()) return null
        val rough = text.lowercase()
        // 전체 문자열 포함 매칭(예: "데님자켓" 같은 붙임표현)
        aliasIndex.entries.firstOrNull { (k, _) -> rough.contains(k) }?.let {
            return it.value
        }

        // 토큰 단위 매칭
        val tokens =
                rough.split(" ", "/", ",", "|", "_", ".", "(", ")", "[", "]", "&", "+", "·")
                        .map { normalize(it) }
                        .filter { it.isNotBlank() }
        tokens.forEach { t ->
            aliasIndex[t]?.let {
                return it
            }
        }
        return null
    }

    /** 서브카테고리 → 레거시 type("T"/"B"/"O") 매핑. 없으면 null */
    fun legacyTypeFor(sub: Subcategory): String? =
            when (sub.category) {
                Category.TOP -> LEGACY_TYPE_TOP
                Category.BOTTOM -> LEGACY_TYPE_BOTTOM
                Category.OUTER -> LEGACY_TYPE_OUTER
                // 기타는 현재 DB 스키마(type) 범위 밖 → null
                else -> null
            }

    /** 서브카테고리 → Try-On 파트("TOP"/"BOTTOM") 매핑 */
    fun tryOnPartFor(sub: Subcategory): String =
            when (sub.category) {
                Category.BOTTOM, Category.SHOES, Category.SOCKS -> TRY_ON_PART_BOTTOM
                else -> TRY_ON_PART_TOP
            }

    /** 서브카테고리에서 추천 태그(한국어/영어 별칭) 생성 */
    fun tagsForSubcategory(sub: Subcategory): Set<String> =
            sub.aliases.map { it.lowercase() }.toSet()

    /**
     * 자유 텍스트에서 태그 후보 생성:
     * - 매칭된 서브카테고리의 별칭 + 텍스트의 명확한 색/형태 토큰(간단 규칙)을 반환
     */
    fun suggestTags(text: String): Set<String> {
        val base = matchSubcategoryFreeText(text)?.let { tagsForSubcategory(it) } ?: emptySet()
        val colorTokens = extractColorTokens(text)
        val sleeveTokens = extractSleeveTokens(text)
        return (base + colorTokens + sleeveTokens).toSet()
    }

    /** 간단 색상 토큰 추출(필요 시 확장 가능) */
    fun extractColorTokens(text: String): Set<String> {
        val t = text.lowercase()
        val known =
                listOf(
                        "white",
                        "black",
                        "gray",
                        "grey",
                        "navy",
                        "beige",
                        "brown",
                        "khaki",
                        "olive",
                        "red",
                        "blue",
                        "green",
                        "yellow",
                        "pink",
                        "purple",
                        "ivory",
                        "cream",
                        "화이트",
                        "블랙",
                        "그레이",
                        "그레이",
                        "네이비",
                        "베이지",
                        "브라운",
                        "카키",
                        "올리브",
                        "레드",
                        "블루",
                        "그린",
                        "옐로우",
                        "핑크",
                        "퍼플",
                        "아이보리",
                        "크림",
                        "회색",
                        "검정",
                        "검정색",
                        "흰색"
                )
        return known.filter { t.contains(it) }.toSet()
    }

    /** 소매/기장 관련 토큰 추출 */
    fun extractSleeveTokens(text: String): Set<String> {
        val t = text.lowercase()
        val known =
                listOf("shortsleeve", "longsleeve", "반팔", "긴팔", "7부", "half sleeve", "롱슬리브", "숏슬리브")
        return known.map { normalize(it) }.filter { t.contains(it) }.toSet()
    }

    /** 카테고리별 대표 키워드(검색 섹션/필터 노출용) */
    fun keywordsFor(category: Category): Set<String> =
            Subcategory.entries
                    .filter { it.category == category }
                    .flatMap { it.aliases }
                    .map { it.lowercase() }
                    .toSet()

    /** 서브카테고리의 상대 보온 보정치(도메인 스코어에 가산/감산 용도) */
    fun warmthBias(sub: Subcategory): Int = sub.warmthBias

    /** 카테고리 → 레거시 type 후보 (현재 스키마 허용 범위) */
    fun legacyTypeCandidates(category: Category): Set<String> =
            when (category) {
                Category.TOP -> setOf(LEGACY_TYPE_TOP)
                Category.BOTTOM -> setOf(LEGACY_TYPE_BOTTOM)
                Category.OUTER -> setOf(LEGACY_TYPE_OUTER)
                else -> emptySet()
            }

    /** 카테고리에서 Try-On 기본 파트 추론 */
    fun defaultTryOnPart(category: Category): String =
            when (category) {
                Category.BOTTOM, Category.SHOES, Category.SOCKS -> TRY_ON_PART_BOTTOM
                else -> TRY_ON_PART_TOP
            }

    /**
     * 키워드 자동완성/추천:
     * - 입력 query를 정규화하여 별칭/키워드에서 prefix 우선 매칭 후 contains 매칭 순으로 반환
     * - category가 주어지면 해당 카테고리 키워드로 범위를 제한
     */
    fun suggestKeywords(query: String, limit: Int = 10, category: Category? = null): List<String> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()
        val source: Set<String> =
                when (category) {
                    null -> allKeywords
                    else -> keywordsFor(category)
                }
        val scored =
                source
                        .map { kw ->
                            val nk = normalize(kw)
                            val score =
                                    when {
                                        nk.startsWith(q) -> 2
                                        nk.contains(q) -> 1
                                        else -> 0
                                    }
                            kw to score
                        }
                        .filter { it.second > 0 }
        return scored.sortedWith(
                        compareByDescending<Pair<String, Int>> { it.second }
                                .thenBy { it.first.length }
                                .thenBy { it.first }
                )
                .map { it.first }
                .distinct()
                .take(limit)
    }
}
