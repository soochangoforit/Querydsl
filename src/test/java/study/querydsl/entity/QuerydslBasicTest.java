package study.querydsl.entity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;



    @BeforeEach
    public void before() {
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");

        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        //member1을 찾아라.
        String qlString = "select m from Member m where m.username = :username";
        Member findMember = em.createQuery(qlString, Member.class).setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }
    @Test
    public void startQuerydsl() {

        //member1을 찾아라.
        JPAQueryFactory queryFactory = new JPAQueryFactory(em);

        QMember m = new QMember("m");

        Member findMember = queryFactory
                .select(m)
                .from(m)
                .where(m.username.eq("member1"))//파라미터 바인딩 처리
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }


    @Test
    public void search() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1").and(member.age.eq(10))
                )
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }


    @Test
    public void searchAndParam() {
        List<Member> result1 = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        member.age.eq(10)
                )
                .fetch();
        assertThat(result1.size()).isEqualTo(1);
    }

    @Test
    public void resultFetch(){
        //List
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();
        //단 건
        Member findMember1 = queryFactory
                .selectFrom(member)
                .fetchOne();

        //처음 한 건 조회
        Member findMember2 = queryFactory
                .selectFrom(member)
                .fetchFirst();

        //페이징에서 사용
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        // 페이징에서 활용 가능
        long total = results.getTotal(); // 조건에 해당하는 count만 가져온다.
        List<Member> content = results.getResults(); // 조건에 해당하는 결과 값 모두를 가져온다.


        //count 쿼리로 변경
        long count = queryFactory
                .selectFrom(member)
                .fetchCount();
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(desc)
     * 2. 회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    /**
     * 페이징
     * fetch를 통해서 조건에 맞는 결과만을 가져온다.
     */
    @Test
    public void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) //0부터 시작(zero index)
                .limit(2) //최대 2건 조회
                .fetch();

        // 전체 페이지에서 1번째 index의 값만 가져오고 2개의 건수를 가져온다.
        assertThat(result.size()).isEqualTo(2);
    }

    /**
     * 페이징에 본격적으로 활용되기 위해서, 쿼리를 작성하는 방법은 다음과 같다.
     * fetchResult를 통해서 where 에 의한 전체 데이터 중에서 조건에 맞는 데이터만 가져오고
     * 조건에 맞는 전체 데이터도 가져온다.
     */
    @Test
    public void paging2() {
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4); // 전체 개수
        assertThat(queryResults.getLimit()).isEqualTo(2); // 한 페이지에 보여질 개수
        assertThat(queryResults.getOffset()).isEqualTo(1); // 몇번째 페이지를 보여주고 있느지

        assertThat(queryResults.getResults().size()).isEqualTo(2); // limit에 의해서 2개만 나왔기 때문이다.
    }


    /**
     * JPQL
     * select
     * COUNT(m), //회원수
     * SUM(m.age), //나이 합
     * AVG(m.age), //평균 나이
     * MAX(m.age), //최대 나이
     * MIN(m.age) //최소 나이
     * from Member m
     */
    @Test
    public void aggregation() throws Exception {
        //tuple은 일종의 map형식으로 데이터가 저장된다고 볼 수 있다.
        List<Tuple> result = queryFactory
                .select(member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min())
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);

        // tuple에서 key 값에 해당하는 값을 조회한다.
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라.
     */
    @Test
    public void group() throws Exception {
        // 주로 groupby로 묶어지는 필드가 select절로 온다.
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                //.having(team.name.eq("team1")) having은 groupby로 묶어준 필드에 대해서 조건을 준다.
                .fetch();

        Tuple teamA = result.get(0); // [ "teamA" : 30 ] 이런식으로 tuple이 구성되어 있다.
        Tuple teamB = result.get(1); // [ "teamB" : 25 ]

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }


    /**
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join() throws Exception {
        QMember member = QMember.member;
        QTeam team = QTeam.team;

        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }


    /**
     * 세타 조인(연관관계가 없는 필드로 조인 , entity가 서로 서로 아무런 연관이 없는 경우)
     * 회원의 이름이 팀 이름과 같은 회원 조회
     *
     * 연관 관계가 없는 테이블에 대해서는 외부 조인이 되지 않았다. 하지만 뒤에서 보면 할 수 있다.
     * 처음에는 외부 조인을 할 수 없었다. 연관 관계가 없는것에 대해서는 세타 조인을 통해서 left join , on을
     * 통해서 해결할 수 있다.
     */
    @Test
    public void theta_join() throws Exception {

        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team) // 세타 조인
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }


    /**
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: SELECT m, t FROM Member m LEFT JOIN m.team t on t.name = 'teamA'
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.TEAM_ID=t.id and t.name='teamA'
     *
     * 이미 Entity 시점에서 각각의 Table마다 Join 속성을 다 정해줬으므로 left join ( ~ ) 안에 2개의 파리미터 값을 넣도록 한다.
     * teamA인 팀만 조인하기 위해서는, 조인 속성에서 추가적인 조건을 부여하기 위해서 on절을 사용한다.
     *
     * join 대상에 대해서 조건절을 넣어서 "필터링된 join 대상"에 대해서는
     * left join(right join)과 on절을 사용하고
     *
     * 일반 내부 조인 같은 경우에는 join , where를 쓰자
     *
     * select가 여러개가 나왔기 때문에 Tuple로 나왔다.
     * tuple = [Member(id=3, username=member1, age=10), Team(id=1, name=teamA)]
     * tuple = [Member(id=4, username=member2, age=20), Team(id=1, name=teamA)]
     * tuple = [Member(id=5, username=member3, age=30), null]
     * tuple = [Member(id=6, username=member4, age=40), null]
     *
     * 만약, 일반 join , on을 사용하게 된다면
     * join , where랑 같은 의미가 된다. 같은 내부 조인이기 때문에
     */
    @Test
    public void join_on_filtering() throws Exception {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }

    }


    /**
     * 2. 연관관계 없는 엔티티 외부 조인
     * 예) 회원의 이름과 팀의 이름이 같은 대상 외부 조인
     * JPQL: SELECT m, t FROM Member m LEFT JOIN Team t on m.username = t.name
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.username = t.name
     *
     * left join을 하였으니 member에 대한 모든 데이터는 다 나온다.
     *
     * 연관관계가 없는 entity에 대해서 조인을 하고자 하기 때문에 -> 막 조인 "카티션 프러덕트'가 된다.
     * 그래서 일반 연관 관계가 있는 leftjoin 이랑 문법이 조금 다른것을 확인할 수 있다.
     *
     * t=[Member(id=3, username=member1, age=10), null]
     * t=[Member(id=4, username=member2, age=20), null]
     * t=[Member(id=5, username=member3, age=30), null]
     * t=[Member(id=6, username=member4, age=40), null]
     * t=[Member(id=7, username=teamA, age=0), Team(id=1, name=teamA)]
     * t=[Member(id=8, username=teamB, age=0), Team(id=2, name=teamB)]
     *
     * 주의! 문법을 잘 봐야 한다. leftJoin() 부분에 일반 조인과 다르게 엔티티 하나만 들어간다.
     * 일반조인: leftJoin(member.team, team)
     * on조인: from(member).leftJoin(team).on(xxx)
     */
    @Test
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("t=" + tuple);
        }
    }




    /**
     * fetch join 미적용, 그냥 일반 lazy loading을 이용한 조인
     */

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo() throws Exception {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        // lazy loading 실현
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }


    /**
     * fetch join 적용
     * 일반 sql에서 아무런 조건이 없는 join과 같다.
     * 혹은 조건절을 넣어주면 조건에 따라서 조인을 할 수 있다.
     *
     * 그러면 일반 join으로 member 와 team은 fetch join과 뭐가 다를까??
     * member를 가져올때 연관된 team의 데이터도 함께 가져와서 select 절에 넣어준다.
     *
     * 1번 fetch join
     * 2번 일반 join
     *
     * fetch join이라는 기능 자체의 핵심은 연관된 엔티티를 한번에 최적화해서 조회하는 기능입니다. 그래서 LAZY가 발생하지 않습니다.
     *
     * 1. findMember.getTeam().getName() -> LAZY 쿼리 발생X
     *
     * 반면에 2번은 LAZY 상태를 유지하고 조회하기 때문에 LAZY 쿼리가 발생할 수 있습니다.
     *
     * 2. findMember2.getTeam().getName() -> LAZY 쿼리 발생O
     *
     *
     */
    @Test
    public void fetchJoinUse() throws Exception {
        em.flush();
        em.clear();
        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        // lazy loading 실현 X
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원 조회
     *
     * 쿼리 안에 또 다른 쿼리 만들기
     */
    @Test
    public void subQuery() throws Exception {

        // member의 qtype이 2번 쓰이기 때문에 서로 다른 Qtype이 필요하다.
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        // 나이 필드를 꺼내고 해당 나이가 40인 사람이 나올것이다. 예상하는 테스트 코드
        assertThat(result).extracting("age").containsExactly(40);
    }

    /**
     * 나이가 평균 나이 이상인 회원
     */
    @Test
    public void subQueryGoe() throws Exception {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age").containsExactly(30,40);
    }

    /**
     * 서브쿼리 여러 건 처리, in 사용
     */
    @Test
    public void subQueryIn() throws Exception {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10)) // 나이가 10살 이상인 모든 회원의 나이 데이터 조회
                ))
                .fetch();

        assertThat(result).extracting("age").containsExactly(20, 30, 40);
    }

    /**
     * 그냥 왼쪽에는 유저 이름을 모두 출력하고 , 오른쪽에는 유저 평균 나이를 출력하고자 한다.
     */
    @Test
    public void selectSubQuery(){

        QMember memberSub = new QMember("memberSub");

        List<Tuple> fetch = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ).from(member)
                .fetch();
        for (Tuple tuple : fetch) {
            System.out.println("username = " + tuple.get(member.username));
            System.out.println("age = " +
                    tuple.get(JPAExpressions.select(memberSub.age.avg())
                            .from(memberSub)));
        }
    }


    @Test
    public void basicCase(){
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }


    @Test
    public void complexCase(){
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();
    }


    /**
     * 프로젝션 , select절에서 결과 1개 조회
     */
    @Test
    public void simpleProjection(){

        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

    }


    /**
     * 프로젝션 , 튜플로 조회
     */
    @Test
    public void tupleProjection(){
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username=" + username);
            System.out.println("age=" + age);
        }
    }


    /**
     * JPQL에서 DTO로 조회하기 위해서는 new operation을 사용해야 한다.
     * 마치 생성자처럼 사용한다. , JPQL에서 지원
     */
    @Test
    public void findDtoByJPQL(){
        List<MemberDto> result = em.createQuery(
                        "select new study.querydsl.dto.MemberDto(m.username, m.age) " +
                                "from Member m", MemberDto.class)
                .getResultList();
    }


    /**
     * Querydsl 을 통해서 DTO 조회 , 프로퍼티 조회 Setter
     * MemberDto에서는 기본 생성자가 필요하다.
     * 기본 생성자를 만들고 , set  ,set
     */
    @Test
    public void findDtoBySetter(){
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
    }

    /**
     * 프로퍼티 접근법 중에서 필드로 주입, getter , setter 필요가 없다.
     */
    @Test
    public void findDtoByField(){
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
    }

    /**
     * 프로퍼티 접근법 중에서 필드로 주입 혹은 Setter를 이용한 주입을 할때는
     * Entity에서 가져오는 프로퍼티 명이랑 DTO의 프로퍼티 명이랑 같아야 한다.
     *
     * 프로퍼티 명이 다를때는 as("name")으로 사용해야 한다.
     *
     * 서브 쿼리 같은 경우는 select에 별칭이 없는데 그것을
     * ExpressionUtils로 한번 감싸줘서 알려준다.
     */
    @Test
    public void findUserDtoByField(){
        QMember memberSub = new QMember("memberSub");

        List<UserDto> fetch = queryFactory
                .select(Projections.fields(UserDto.class,
                                member.username.as("name"),

                                ExpressionUtils.as(
                                        JPAExpressions
                                                .select(memberSub.age.max())
                                                .from(memberSub), "age")
                        )
                ).from(member)
                .fetch();
    }


    /**
     * 프로퍼티 접근법에서 생성자 접근 방식
     * 생성자의 타입 순서에 맞게 넣어줘야 한다.
     */
    @Test
    public void findDtoByConstructor(){
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
    }


    /**
     * MemberDto 생성자에다가 @QueryProjection 어노테이션을 넣어주고
     * compileQuerydsl 실행 -> dto도 qType으로 생성
     */
    @Test
    public void findDtoByQueryProjection(){
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();
    }


    /**
     * 검색 조건으로 usernmae 이름이 member1이고
     * 나이가 10살인 사람을 조회하는 쿼리
     */
    @Test
    public void 동적쿼리_BooleanBuilder() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;
        List<Member> result = searchMember1(usernameParam, ageParam);

        Assertions.assertThat(result.size()).isEqualTo(1);
    }
    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        BooleanBuilder builder = new BooleanBuilder();

        // and 조건 추가
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }
        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }
        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }


    /**
     * where 다중 파라미터 사용
     */
    @Test
    public void 동적쿼리_WhereParam() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);

        Assertions.assertThat(result.size()).isEqualTo(1);
    }
    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(allEq(usernameCond, ageCond))
                .fetch();
    }
//    private BooleanExpression usernameEq(String usernameCond) {
//        return usernameCond != null ? member.username.eq(usernameCond) : null;
//    }
//    private BooleanExpression ageEq(Integer ageCond) {
//        return ageCond != null ? member.age.eq(ageCond) : null;
//    }

    /**
     * BooleanExpression에 대해서 조합 가능
     * null 처리를 따로 해줘야 한다.
     */
    private BooleanBuilder allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    private BooleanBuilder ageEq(Integer age) {
        return nullSafeBuilder(() -> member.age.eq(age));
    }

    private BooleanBuilder usernameEq(String name) {
        return nullSafeBuilder(() -> member.username.eq(name));
    }


    public static BooleanBuilder nullSafeBuilder(Supplier<BooleanExpression> f) {
            try {
                return new BooleanBuilder(f.get());
            } catch (IllegalArgumentException e) {
                return new BooleanBuilder();
            }
        }




//    @DataJpaTestpublic
//    class DynamicQueryTest {
//        JPAQueryFactory queryFactory;
//        @Autowired
//        EntityManager em;
//
//        @BeforeEach
//        void init() {
//            queryFactory = new JPAQueryFactory(em);
//            em.persist(new Member("userA", 10, "ROLE_MASTER"));
//            em.persist(new Member("userB", 20, "ROLE_ADMIN"));
//            em.persist(new Member("userC", 30, "ROLE_USER"));
//        }
//
//        @Test
//        void dynamicQuery() {
//            Integer age = 10;
//            String role = "ROLE_MASTER";
//
//            List<Member> result = queryFactory
//                    .selectFrom(member)
//                    .where(ageAndRoleEq(age, role))
//                    .fetch();
//            System.out.println("result = " + result);
//        }
//
//        private BooleanBuilder ageAndRoleEq(Integer age, String role) {
//            return ageEq(age).and(roleEq(role));
//        }
//
//        private BooleanBuilder ageEq(Integer age) {
//            if (age == null) {
//                return new BooleanBuilder();
//            } else {
//                return new BooleanBuilder(member.age.eq(age));
//            }
//        }
//
//        private BooleanBuilder roleEq(String roleName) {
//            if (roleName == null) {
//                return new BooleanBuilder();
//            }
//
//            return new BooleanBuilder(member.roleName.eq(roleName));
//        }
//    }
//
//}
//
//
//        private BooleanBuilder ageEq(Integer age) {
//            return nullSafeBuilder(() -> member.age.eq(age));
//        }
//
//        private BooleanBuilder roleEq(String roleName) {
//            return nullSafeBuilder(() -> member.roleName.eq(roleName));
//        }
//
//        public static BooleanBuilder nullSafeBuilder(Supplier<BooleanExpression> f) {
//            try {
//                return new BooleanBuilder(f.get());
//            } catch (IllegalArgumentException e) {
//                return new BooleanBuilder();
//            }
//        }


    /**
     * 벌크성 수정 연산
     */
    @Test
    public void bulkUpdate(){
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();
    }


    @Test
    public void bulkUpdate2(){
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                .execute();
    }























}
