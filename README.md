![image](./images/lawtalk_CI.png)

Repositories
- <b>상담관리</b>    - https://github.com/JayTHun/consult.git
- <b>일정관리</b>    - https://github.com/JayTHun/schedule.git
- <b>마이페이지</b>  - https://github.com/JayTHun/board.git
- <b>게이트웨이</b>  - https://github.com/JayTHun/gateway.git
- <b>결제관리</b>    - https://github.com/JayTHun/payment.git
# Consult-Lawer (법률 상담 서비스)

# Table of contents

- [법률 상담 (talk-Lawer) 서비스]
  - [서비스 시나리오](#서비스-시나리오)
    - 요구사항
  - [체크포인트](#체크포인트)
  - [분석/설계](#분석설계)
    - 개요 및 구성 목표
    - 서비스 설계를 위한 Event Storming
    - 헥사고날 아키텍처 다이어그램 도출
  - [구현방안 및 검증](#구현)
    - [DDD 의 적용](#ddddomain-driven-design-의-적용)
    - [CQRS 구현](#cqrs-구현)
    - [Polyglot Persistence](#Polyglot-Persistence)
    - [SAGA / Correlation](#saga--correlation)
    - [동기식 호출 과 Fallback 처리](#동기식-호출과-fallback-처리)
    - [비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종(Eventual) 일관성](#비동기식-호출--시간적-디커플링--장애격리--최종-eventual-일관성)
  - [베포 및 운영](#배포-및-운영)
    - [CI/CD 설정](#cicd-설정)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출--서킷-브레이킹--장애격리)
    - [오토스케일 아웃](#오토스케일-아웃-hpa)
    - [무정지 재배포](#zero-downtime-deploy-readiness-probe)
    - [항상성 유지](#self-healing-liveness-probe)


# 서비스 시나리오


## 요구사항

- 기능적 요구사항
 1. 사용자(고객, 피상담원)는 APP(or 웹페이지)에서 상담을 요청한다.   
 2. 사용자는 선택한 상담유형에 따라 안내된 수수료(상담료)를 미리 결제한다.
 3. 결제가 완료되면, 상담 요청이 상담원(‘변호사’)에게 전달된다.
 4. 상담원은 상담 요청 정보가 도착하면, 상담을 수락한다. 
 5. 상담 요청이 수락되면 사용자는 APP(or 웹페이지)에서 진행상태를 조회할 수 있다.
 6. 사용자의 변심으로 상담 요청을 취소할 수 있다.
   6-1. 상담이 불가한 지역인 경우, 자동으로 거부될 수도 있다.
 7. 상담 요청이 취소되면 결제는 취소된다.
 8. 사용자는 요청한 상담 건에 대한 진행 내역을 myPage를 통해서 조회 가능하다.(CQRS)


- 비기능적 요구사항

 1. 트랜잭션
 
    - 상담료가 결제가 되지 않는 경우, 상담 요청이 되지않아야 한다. 
    - 상담 요청이 취소되면 결제가 취소되고,  상담 요청 정보 또한 업데이트가 되어야 한다.
    - 사용자의 상담 요청이 거부된 경우, 자동으로 상담 요청 취소되고,상담료 결제가 취소되고,상담 요청 정보가 업데이트가 되어야 한다.
    
  
 2. 장애격리
 
    - 상담 요청은 24시간 가능해야 한다.
    - 결제 시스템에 과부하 발생 시, 잠시 후에 다시 하도록 유도한다.


# 체크포인트


## 평가항목
 1. Saga
 2. CQRS
 3. Correlation
 4. Req/Resp
 5. Gateway
 6. Deploy/Pipeline
 7. Circuit Breaker
 8. Autoscale (HPA)
 9. Zero-downtime deploy (Readiness Probe)
 10. Config Map/Persistence Volume
 11. Polyglot
 12. Self-healing (Liveness Probe)


# 분석/설계:


## AS-IS 조직 (Horizontally-Aligned)

![image](./images/tl_as-is.png)


## TO-BE 조직 (Vertically-Aligned)

![image](./images/tl_to-be.png)

## 서비스 설계를 위한 Event Storming

- **Event 도출 -> 부적격 Event 탈락 -> Actor/Commend 부착 -> Aggregate 묶기 -> Bounded Context 묶기 -> Policy 도출, 이동 -> Context Mapping -> 1차 모델 도출 -> 요구사항 검증 및 보완 -> 최종 모델**


### Events 도출
![image](./images/tl_event_candidates.png)


### 부적격 Event 탈락
![image](./images/tl_event_filtering.png)

  
  과정 중 도출된 여러 이벤트 중 잘못되거나 프로젝트 범위에 맞지 않는 도메인 이벤트들을 걸러내는 작업을 수행함

    - 상담정보(구분,유형,가격)입력됨, 상담료 결제요청됨, 상담료 결제거부됨, 상담리스트 확인됨, 상담수락 취소됨, 상담요청수락알림수신됨

### Actor, Command 부착하여 가독성개선
![image](./images/tl_actor_command.png)


    - Event를 발생시키는 Command와 Command를 발생시키는주체, 담당자 또는 시스템을 식별함
    - Actor : 고객(피상담임), 변호사(상담인), 결제시스템(표시하진않음)
    - Command : 상담 요청/취소, 상담요청 수락/취소, 결제, 결제취소


### Aggregate으로 묶기
![image](./images/tl_aggregate.png)

    - 연관있는 도메인 이벤트들을 Aggregate 로 묶었음 
    - Aggregate : 상담요청, 요청수락, 결제    


### Bounded Context로 묶기
![image](./images/tl_bounded_context.png)


### Policy 부착
![image](./images/tl_policy_attached.png)


### Policy의 이동 / Context Mapping / 1차 Model
![image](./images/policy_cm_1st.png)

    - 컨텍스트 매핑, 서비스 특성을 고려하여 이름 변경 및 영문 전환
    - 각 Actor가 확인하는 MyPage(view) 추가


### 1차 Model에 대한 시나리오 체크

![image](./images/tl_check.png)

####  기능적 요구사항 Coverage Check
  1) 사용자(고객, 피상담원)는 APP(or 웹페이지)에서 상담을 요청한다.
  2) <u>*사용자는 선택한 상담유형에 따라 안내된 수수료(상담료)를 미리 결제한다.*</u>
  3) 결제가 완료되면, 상담 요청이 상담원(변호사)에게 전달된다.
  4) 상담원은 상담 요청 정보가 도착하면, 상담을 수락한다.
  5) <u>*상담 요청이 수락되면 사용자는 APP(or 웹페이지)에서 진행상태를 조회할 수 있다.*</u> 
  6) 사용자의 변심으로 상담 요청을 취소할 수 있다.
  7) 상담 요청이 취소되면 결제는 취소된다.
  8) <u>*사용자는 요청한 상담 건에 대한 진행 내역을 myPage를 통해서 조회 가능하다.*</u>

####  비기능적 요구사항 Coverage Check
  A) 장애와 무관하게 상담 요청은 24시간 가능해야 한다.
  B) 결제 시스템에 과부하 발생 시, 잠시 후에 다시 하도록 유도한다.  

### 최종 Model

![img](./images/tl_final_step1.png)

   
### 최종 Model (기능 추가)
![img](/images/tl_final_step2.png)

    - 이벤트 분리 (ConsultMade->ConsultMade, ConsultPayed) 및 이벤트 추가(ScheduleReceived)
    - 법률상담시스템에서 미리 정한 상담가능지역 외에는 상담이 자동거절 및 결제취소되도록 하는 시나리오('6-1') 추가, 관련 폴리시(denyConsult)와 이벤트 추가(consultDenied, scheduleDenied)
      
## 헥사고날 아키텍처 다이어그램 도출

![img](./images/tl_hexagonal.png)

    - Chris Richardson, MSA Patterns 참고하여 Inbound adaptor와 Outbound adaptor를 구분함
    - 호출관계에서 PubSub 과 Req/Resp 를 구분함
   
   
# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 마이크로서비스를 **Spring Boot**로 구현하였으며, 구현한 각 서비스 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8080 ~ 8084 이다)

```
cd gateway
mvn spring-boot:run

cd consult
mvn spring-boot:run  

cd payment
mvn spring-boot:run 

cd schedule
mvn spring-boot:run

cd board
mvn spring-boot:run  
```
   
   
## DDD(Domain-Driven-Design) 의 적용
이벤트 스토밍(msaez.io)을 통해 구현한 Aggregate 단위로 Entity 를 정의, 선언 후 구현을 진행하였다. Entity Pattern 과 Repository Pattern을 적용하고, 데이터 접근 어댑터를 자동 생성하기 위하여 하기 위해 Spring Data REST 의 RestRepository 를 적용하였다.

- Schedule 서비스의 Schedule.java
```java

package talklawer.domain;

import talklawer.event.ScheduleAccepted;
import talklawer.event.ScheduleReceived;
import talklawer.event.ScheduleCancelled;
import talklawer.event.ScheduleDenied;
import org.springframework.beans.BeanUtils;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Schedule {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long scheduleId;
    private String lawerName;
    private ScheduleStatus scheduleStatus;
    private Long consultId;      //consultee information
    private String mobile;       //consultee information
    private String location;     //consultee information
    private Integer payAmount;   //consultee information

    // 상담접수 (수락 or 불가)
    @PostPersist
    public void onPostPersist() {
        System.out.println("###########################");
        System.out.println(" Schedule onPostPersist")        
        System.out.println("###########################");

        if (this.getScheduleStatus() == ScheduleStatus.RECEIVED) {          
            System.out.println("###########################");
            System.out.println("접수되었습니다. (대면상담을 위한 이동이 가능한 지역)");
            System.out.println("###########################");
            
            ScheduleReceived scheduleReceived = new ScheduleReceived();
            BeanUtils.copyProperties(this, scheduleReceived);
            scheduleReceived.publishAfterCommit();
        } else if (this.getScheduleStatus() == ScheduleStatus.DENIED) {          
            System.out.println("###########################");
            System.out.println(" ### 대면 상담이 불가한 지역입니다. ###");          
            System.out.println("###########################");

            ScheduleDenied scheduleDenied = new ScheduleDenied();
            BeanUtils.copyProperties(this, scheduleDenied);
            scheduleDenied.publishAfterCommit();
        }
    }

    // 상담을 수락 및 취소 시
    @PostUpdate
    public void onPostUpdate() {
        System.out.println("###########################");
        System.out.println("Schedule onPostUpdate");
        System.out.println("###########################");

        if (this.getScheduleStatus() == ScheduleStatus.SCHEDULING) {      
            System.out.println("###########################");
            System.out.println(" 변호사가 상담을 수락하였습니다.");      
            System.out.println("###########################");

            ScheduleAccepted scheduleAccepted = new ScheduleAccepted();
            BeanUtils.copyProperties(this, scheduleAccepted);
            scheduleAccepted.publishAfterCommit();

        } else if (this.getScheduleStatus() == ScheduleStatus.DENIED) {      
            System.out.println("###########################");
            System.out.println("대면 상담이 불가한 지역입니다.");      
            System.out.println("###########################");

            ScheduleDenied scheduleDenied = new ScheduleDenied();
            BeanUtils.copyProperties(this, scheduleDenied);
            scheduleDenied.publishAfterCommit();

        } else  if (this.getScheduleStatus() == ScheduleStatus.CANCELLED) {      
            System.out.println("###########################");
            System.out.println("상담이 취소되었습니다.");      
            System.out.println("###########################");

            ScheduleCancelled scheduleCancelled = new ScheduleCancelled();
            BeanUtils.copyProperties(this, scheduleCancelled);
            scheduleCancelled.publishAfterCommit();
        }
    }
}

```

- Schedule 서비스의 ScheduleRepository.java
```java

package talklawer.domain;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

@RepositoryRestResource(collectionResourceRel="schedule", path="schedule")
public interface ScheduleRepository extends PagingAndSortingRepository<Schedule, Long> {

    public Optional<Schedule> findByConsultId(Long consultId);
}

```
   
다음은 REST API를 호출하여 정상적으로 동작되는지 확인한 결과이다.
- Consult에서 고객이 법률 상담 요청    
![image](./images/flowCheck1_consultMade.png)

- Consult에서 고객이 상담료 결제   
![](/images/flowCheck2_consultPayed.png)


- Schedule에서 상담원이 일정 수락   
![](/images/flowCheck4_scheduleAccepted.png)


- 상담 일정 확인  
![](/images/flowCheck5_checkSchedule.png)


- 사용자의 변심으로 인한 상담 취소  
![image](./images/flowCheck6_consultCancelled.png)


- 상담 취소에 따른, 결제 취소  
![image](./images/flowCheck7_checkPayment.png)


- 상담 취소에 따른, 변호사 일정 취소  
![image](./images/flowCheck8_checkSchedule.png)


- 마이페이지 이력조회
![image](./images/flowCheck9_checkBoard.png)


## CQRS 구현
Board 서비스는 CQRS 패턴을 적용, 타 마이크로서비스의 데이터 원본에 접근없이 잦은 조회가 가능하게 구현하였다. 법률 상담 서비스 프로젝트의 View 역할은 myPage 서비스가 수행한다.
![](/images/tl_cqrs_2.PNG)

![](/images/tl_cqrs_1.PNG)


## Polyglot Persistence

Consult, Payment, Schedule는 H2 Database, Board는 HSQL Database를 사용하였으며, 이를 통하여 MSA간 서로 다른 종류의 DB간에도 문제 없이 동작하여 다형성을 만족하는지 확인하였다.

|서비스|DB|pom.xml|
| :--: | :--: | :--: |
|board| HSQL |![image](./images/tl_polyglot_h2.PNG)|
|consult| H2 |![image](./images/tl_polyglot_hsql.PNG)|
|schedule| H2 |![image](./images/tl_polyglot_hsql.PNG)|
|payment| H2 |![image](./images/tl_polyglot_hsql.PNG)|


## SAGA / Correlation
사용자에 의해 상담이 요청되고 결제가 완료되면, 상담인(변호사)에게 해당 내용이 전달된다. 하지만, 변호사일정관리 시스템에서 미리 정의한 '대면상담 가능지역'일 경우에만 해당 내용을 확인 및 수락할 수 있으며, 그 외의 경우에는 자동 거절, 결제 취소, 그리고 사용자에게도 상담이 거절되었음을 알리도록 구현하였다.
(correlation key는 최초 상담요청시 발생하는 consultId)tl_saga_1.png

- 고객이 상담요청 및 결제 완료 후, Schedule(일정관리시스템)에 해당 내용 전달
![image](./images/tl_saga_1.png)


- 해당 시스템에서 가능지역 확인 후, 상담건에 대해서 거절
![image](./images/tl_saga_2.png)


- 결제 시스템에서 결제내역 취소
![image](./images/tl_saga_3.png)


- 고객의 상담요청건에 대해서도 거절처리
![image](./images/tl_saga_4.png)


상담요청 시, 발생한 consultId를 correlation 키로 사용
```java
package talklawer;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import talklawer.config.kafka.KafkaProcessor;
import talklawer.domain.ScheduleStatus;
import talklawer.domain.Schedule;
import talklawer.domain.ScheduleRepository;
import talklawer.event.ConsultCancelled;
import talklawer.event.ConsultPayed;

@Service
public class PolicyHandler{
    @Autowired
    ScheduleRepository scheduleRepository;

    // Consult에서 정상 결제된 상담건에 대해서 receive 처리한다(consultPayed --pubsub-- schedule을 생성).
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverConsultPayed_receiveSchedule(@Payload ConsultPayed consultPayed){

        if(!consultPayed.validate()) return;
        System.out.println("\n\n##### listener receiveSchedule of Lawer : " + consultPayed.toJson() + "\n\n");

        Optional<Schedule> optionalSchedule = scheduleRepository.findByConsultId(consultPayed.getConsultId());
```
   
board 서비스에서 최초 상담요청부터 요청건에 대한 거절까지, SAGA 패턴이 적용되는 전체 현황을 확인할 수 있다. 
![image](./images/tl_saga_5.png)
   

## Gateway 적용

gateway를 이용하여, 서비스 진입점을 단일화할 수 있다.

application.yaml 설정
```yaml

server:
  port: 8080

---

spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: consult
          uri: http://localhost:8081
          predicates:
            - Path=/consult/**
        - id: payment
          uri: http://localhost:8082
          predicates:
            - Path=/payment/**
        - id: schedule
          uri: http://localhost:8083
          predicates:
            - Path=/schedule/**
        - id: board
          uri: http://localhost:8084
          predicates:
            - Path=/board/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

---

spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: consult
          uri: http://consult:8080
          predicates:
            - Path=/consult/**
        - id: payment
          uri: http://payment:8080
          predicates:
            - Path=/payment/**
        - id: schedule
          uri: http://schedule:8080
          predicates:
            - Path=/schedule/**
        - id: board
          uri: http://board:8080
          predicates:
            - Path= /board/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true 

server:
  port: 8080
```

## gateway 적용

gateway와 ingress를 통한 서버스 인바운드 연결 지원을 테스트 한다.

1. Gateway
- 로컬의 Hosts 파일에 각 서비스들을 external-IP를 이용하여 등록
![image](./images/tl_gateway_test3.png)
![image](./images/tl_gateway_test4.png)
![image](./images/tl_gateway_test5.png)


- 상담요청 및 확인이 정상적으로 수행되는지 확인
![image](./images/tl_gateway_test6.png)
![image](./images/tl_gateway_test7.png)


## 동기식 호출과 Fallback 처리
고객이 최초 상담 요청 시, 결제를 수행해야만 변호사가 상담요청을 받을 수 있도록 하였으며, 이를 구현하기 위하여 호출 프로토콜은 Rest Repository에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 동기호출하도록 하였다.

- Consult 서비스 내의 external.PaymentService 
```java
package talklawer.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import talklawer.util.PaymentResult;
import java.util.HashMap;

@FeignClient(name="payment", url="${api.url.payment}", fallback = PaymentServiceFallback.class)
public interface PaymentService {
    @RequestMapping(method= RequestMethod.POST, path="/payment/approve")
    public PaymentResult approve(@RequestBody HashMap<String, String> map);
}
```

```java
package talklawer.external;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import talklawer.util.PaymentResult;
import java.util.HashMap;

@Component
public class PaymentServiceFallback implements PaymentService {
    @Override
    public PaymentResult approve(@RequestBody HashMap<String, String> map) {
        PaymentResult paymentResult = new PaymentResult();
        paymentResult.setResultCode(-2L);
        return pr;
    }
}
``` 
   

- Payment 서비스에 구현되어 있는 REST API()
```java
package talklawer.controller;

import talklawer.domain.PayType;
import talklawer.domain.Payment;
import talklawer.domain.PaymentRepository;
import talklawer.util.PaymentResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Optional;

@RestController
public class PaymentController {
    @Autowired
    PaymentRepository paymentRepository;

    @RequestMapping(value = "/payment/approve",
            method = RequestMethod.POST,
            produces = "application/json;charset=UTF-8")
    public PaymentResult approve(@RequestBody HashMap<String, String> map) {

        PaymentResult paymentResult = new PaymentResult();
        try {
            String consultId = this.getParam(map, "consultId", true);
            String payType   = this.getParam(map, "payType", true);
            String payAmount = this.getParam(map, "payAmount", false);
            String mobile    = this.getParam(map, "mobile", true);          

            Payment payment = Payment.approve(
                    PayType.valueOf(payType),
                    Integer.valueOf(payAmount),
                    Long.valueOf(consultId)
            );
            paymentRepository.save(payment);

            paymentResult.setResultCode(1L);
            paymentResult.setResultMessage(String.valueOf(payment.getPaymentId()));
            return pr;

        } catch (Exception e) {
            System.out.println("<<<<< Sorry. Cannot make payment entity >>>>> ");
            System.out.println(e.getMessage());

            paymentResult.setResultCode(-1L);
            paymentResult.setResultMessage(e.getMessage());
            return pr;
        }
    }
```
   

- Consult 서비스에서 PaymentService를 동기 방식으로 호출
```java
package talklawer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import talklawer.ConsultApplication;
import talklawer.domain.Consult;
import talklawer.domain.ConsultRepository;
import talklawer.domain.ConsultStatus;
import talklawer.exception.PaymentException;
import talklawer.util.PaymentResult;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Optional;

@RestController
public class ConsultController {

    @Autowired
    ConsultRepository consultRepository;

    @RequestMapping(value = "/consult/payConsult/{consultId}",
            method = RequestMethod.GET,
            produces = "application/json;charset=UTF-8")
    public void payConsult(@PathVariable Long consultId) throws Exception {

        Optional<Consult> consult = consultRepository.findById(consultId);

        if (!consult.isPresent()) {
            throw new InvalidParameterException("<<< 상담요청건을 찾을 수 없습니다 >>>");
        }
        Consult theConsult = consult.get();

        if (theConsult.getStatus() == ConsultStatus.APPROVED) {
            throw new RuntimeException("<<< 해당 상담건은 이미 결제된 상태입니다. >>>");
        }

        HashMap<String, String > map = new HashMap<String, String>();
        map.put("consultId",    String.valueOf(theConsult.getConsultId()));
        map.put("mobile",    theConsult.getMobile());
        map.put("payType",   String.valueOf(theConsult.getPayType()));
        map.put("payAmount", String.valueOf(theConsult.getPayAmount()));

        // PaymentService에게 승인을 요청한다.
        PaymentResult paymentResulr = ConsultApplication.applicationContext.getBean(talklawer.external.PaymentService.class)
                .approve(map);

        // Payment에 실패한 경우 Exception 처리
        if (paymentResulr.getResultCode().equals(-2L)) {
            throw new PaymentException("<<< PaymentService : No-Response or Timed-out. please, try later... >>>");
        } else if (paymentResulr.getResultCode().equals(-1L)) {
            throw new PaymentException("<<< PaymentService : 결제 처리에 실패하였습니다. :: " + paymentResulr.getResultMessage() + " >>>");
        } else {
            theConsult.setStatus(ConsultStatus.APPROVED);
            theConsult.setPaymentId(paymentResulr.getResultCode());
            consultRepository.save(theConsult);
        }
    }
}
```

- 결제서비스 Payment에서 문제가 발생 시, 아래와 같이 처리된다. 
![img](images/tl_syncfallback_1.png)

![img](images/tl_syncfallback_1.png)
     
   
## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성
본 시스템에 적용되어 있는 주요 비동기 호출은 다음과 같다.
```
  • Call 결제시, Catcher에게 알려줌 (CallPayed --> receiveCall)
  • 결제된 Call 취소시, Payment에서도 취소되도록 하며 (CallCancelled --> cancelPayment)
  • Catcher에게도 Call이 취소된 상태임을 알려준다. (CallCancelled --> cancelCatch)
  • Catcher가 서비스 불능 지역으로 접수 안될 경우 Payment에게  알려준다. (CatchDenied --> disablePayment)
```
그 외에도 각 마이크로서비스에서 콜이 처리되는 상태를 알 수 있도록 Dashboard 서비스에게 이벤트를 전달하여 모든 기록이 남도록 한다.
```  	
  • Caller : CallMade, CallPayed, CallCancelled
  • Payment : PaymentApproved,PaymentCancelled, PaymentDisabled
  • Catcher : CallReceived, CallCaught, CatchDenied 
```
이를 위해 각각의 마이크로서비스에 이벤트를 수신 처리하는 PolicyHandler를 구현하였다.

- Dashboard의 PolicyHandler 
```java
   // 대시보드를 생성한다.
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCallMade_inputRecord(@Payload CallMade callMade){
        if(!callMade.validate()) return;

        String extraIdName = "";
        Long   extraIdValue = null;
        String description = " mobile=" + callMade.getMobile() +
                            ", location=" + callMade.getLocation() +
                            ", payType=" + callMade.getPayType() +
                            ", payAmount=" + callMade.getPayAmount() ;
        inputRecord(callMade, extraIdName, extraIdValue, description);
    }
    ...

    // 콜 접수 대시보드 생성
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCallReceived_inputRecord(@Payload CallReceived callReceived){
        if(!callReceived.validate()) return;

        String extraIdName = "";
        Long   extraIdValue = null;
        String description = " mobile=" + callReceived.getMobile() +
                ", location=" + callReceived.getLocation() + " : 콜 접수 대기중입니다. ";
        inputRecord(callReceived, extraIdName, extraIdValue, description);
    }

    // 콜 거절 대시보드 생성
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCatchDenied_inputRecord(@Payload CatchDenied catchDenied){
        if(!catchDenied.validate()) return;

        String extraIdName = "";
        Long   extraIdValue = null;
        String description = " mobile=" + catchDenied.getMobile() +
                ", location=" + catchDenied.getLocation() + " : 서비스 불가 지역입니다."  ;
        inputRecord(catchDenied, extraIdName, extraIdValue, description);
    }

    ...

 ```
 
위와 같은 비동기 방식으로 콜요청 및 결제시스템과 대리기사용 Catcher 시스템이 분리되어 있기 때문에 잠시 Catcher시스템에 장애가 있더라도 Catcher 시스템을 재기동한 후, 요청된 콜을 확인할 수 있다.
1) catcher가 가동되지 않은 상태에서 콜 결제를 처리한다. 
![](/images/cal262-catcher-killed.png)   
_(catcher가 없어도 콜 요청 및 결제가 실행된다.)_

1) catcher가 가동되면서 밀려있는 콜을 수신한다.
![](/images/cal262-catcher-alive.png)   
_(catcher 서비스가 수행된 후 대기중인 콜 요청을 수신한다.)_
   
   
   
   
   

# 배포 및 운영:

## CI/CD 설정
### 도커 이미지 및 컨테이너 배포   
각 마이크로 서비스별로 build 후에 docker 이미지를 azure 레지스트리에 올린다. 
- **Build 및 Dockerizing** 
```shell
# 프로젝트 디렉토리에서 시작
cd caller
mvn package -Dmaven.test.skip=true
docker build -t nicecall.azurecr.io/caller:latest .
docker push nicecall.azurecr.io/caller:latest 

cd ../catcher
mvn package -Dmaven.test.skip=true
az acr login --name nicecall
docker build -t nicecall.azurecr.io/catcher:latest .
docker push nicecall.azurecr.io/catcher:latest 
...
```
_위와 같은 방식으로 나머지 마이크로서비스 프로젝트에 대해서도 수행한다._
   
- **namespace 및 deboloyment, service 생성**
```shell
# namespace 생성
kubectl create namespace nicecall
kubectl config set-context --current --namespace=nicecall

# caller deployment, service 생성
kubectl apply -f ../caller/azure/deploy.yaml
kubectl apply -f ../caller/azure/service.yaml

# catcher deployment, service 생성
kubectl apply -f ../catcher/azure/deploy.yaml
kubectl apply -f ../catcher/azure/service.yaml
..
```
_(위와 같은 방식으로 나머지 마이크로서비스 프로젝트에 대해서도 수행한다.)_
   
      
각 마이크로서비스에 Deployment, Service생성에 사용된 yaml 파일은 아래와 같다. 
- Deployment.yaml
```yaml
apiVersion : apps/v1
kind: Deployment
metadata:
  name: caller
  namespace: nicecall
  labels:
    app: caller
spec:
  replicas: 1
  selector:
    matchLabels:
      app: caller
  template:
    metadata:
      labels:
        app: caller
    spec:
      containers:
        - name: caller
          image: nicecall.azurecr.io/caller:latest
          ports:
            - containerPort: 8080
```
_(참고로 위의 yaml 파일은 가장 기본적인 형태이다.(각 마이크로서비스 특성에 따라 다른 속성이 추가된다.)_
   
   
![](/images/cal262-microservice-deployed.png)   
_(각 마이크로서비스 컨테이너가 cloud에서 생성되고 있는 모습)_
  
- Service.yaml
```yaml
apiVersion: v1
kind: Service
metadata:
  name: caller
  namespace: nicecall
  labels:
    app: caller
spec:
  ports:
    - port: 8080
      targetPort: 8080
  selector:
    app: caller
```

![](/images/cal262-microservice-running.png)
_(각 마이크로서비스가 cloud에 running 된 모습)_
   

### 자동화된 DevOps Pipeline 적용 
서비스가 안정되면 Azure Cloud DevOps를 활용하여 다음과 같이 Pipeline을 작성하여 CI/CD를 자동화한다.
- caller 마이크로서비스에 대해 CI/CD Pipeline 생성한 모습
  ![](/images/cal262-pipeline-CI.png)
  ![](/images/cal262-pipeline-CD.png) 

- Step1. Github에 변경사항 push 한다.
![](/images/cal262-pipeline-triggered.png)
- Step2. DevOps CI pipeline이 실행됨
![](/images/cal262-pipeline-CI-res1.png)
![](/images/cal262-pipeline-CI-res2.png)

- Step3. DevOps CD pipeline이 start됨
![](/images/cal262-pipeline-CD-res1.png)

- Step4. Caller 서비스가 cloud에서 실행되는 모습
![](/images/cal262-pipeline-CD-res2.png)  
   
   
## ConfigMap 적용
변경 가능성이 높은 속성 정보에 대해서는 다음과 같이 ConfigMap을 적용하여 구현하였다.
1) 대리운전 서비스 불가 지역에 대해 소스코드내에 다음과 같이 application 속성 값을 받아들이도록 지정
```java
        ...
    
    @Value("${catcher.service.area}")
    String svcAreas;
    // 전달받은 요청이 서비스 지역에 포함되어 있는지 검증한다.
    private Boolean outOfService(String location) {

        System.out.println(" CATCHER SERVICE AREA : " + svcAreas);
        String [] arrSvcArea = svcAreas.split(",");

        for (int i = 0; i < arrSvcArea.length ; i++) {
            String s = arrSvcArea[i].toLowerCase();

            // 서비스 지역이면 false 리턴
            if (s.equals(location.toLowerCase())) return false;

        }
        return true;
    }
```
1) application.yml 파일 내부에서는 환경 변수를 통해 전달받도록 설정 
```yaml
# application.yml
catcher:
  service:
    area: ${catcher-area}   # 환경 변수
```

1) deployment 내부에서는 catcher-area 환경변수의 값에 대하여  catcher-cm이라는 configMap을 참조시킴
```yaml
# deploy.yaml
env:
  - name: catcher-area
    valueFrom:
      configMapKeyRef:
        name: catcher-cm
        key: area
```
4) 최종적으로 configMap 안에 다음과 같이 고정 값을 넣어두고 쉽게 수정할 수 있도록 구현함
```yaml
# catch-cm.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: catcher-cm
  namespace: nicecall
data:
  area: kangnam,dongjak,nowon,mapo,jongro,bundang
```
이와 같이 configMap에 설정된 값이 적용되어, 서비스 가용 지역이 아닌 콜이 수신될 경우 Catcher에 에 의해 거부 처리되는 모습을 아래와 같이 확인할 수 있다.   
![](/images/cal262-cm-ex1.png)    
   
## Persistence Volume
nicecall-pvc.yaml 파일을 이용하여 persistanceVolumn 선언하였다.
```yaml
# nicecall-pvc.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: nicecall-disk
  namespace: nicecall
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: azurefile
  resources:
    requests:
      storage: 1Gi
```
선언된 디스크 사용을 위해 caller와 catcher의 deploy.yaml에 Volumn 및 Mount 정보는 다음과 추가한다. 
```yaml
# deploy.yaml
volumeMounts:
  - mountPath: "/mnt/azure"
    name: volume
volumes:
  - name: volume
    persistentVolumeClaim:
      claimName: nicecall-disk
```
application.yml에는 해당 볼륨에 사용할 경로를 지정한다.(caller의 설정 내용)
```yaml
# application.yml
logging:
  level:
    root: info
  file: /mnt/azure/logs/caller.log
```
_(위와 같이 catcher의 설정 파일에도 로그 파일 위치를 지정)_

해당 로그 파일이 계속 누적하여 쌓이고 있는지는 다음과 같이 확인한다.
![](/images/cal262-pvc-logfile.png)
 
  
----
## 동기식 호출 / 서킷 브레이킹 / 장애격리
서킷 브레이커는 Spring FeignClient + Hystrix 를 적용하여 구현하였다.  

동기호출 방식의 콜 결제(Caller --> Payment) 부분은 Feign Client와 Fallback을 적용하여 구현하였음을 이미 위에서 제시하였다.   
본 시나리오에서는 hystrix를 통해 timeout 임계값을 설정하고 부하가 발생할 경우 어떻게 Circuit Break가 작동되는지를 확인한다. 

- Hystrix 테스트를 위해 callId=999의 조건의 경우 0.8~1.2초 정도 sleep이 발생하도록 Payment Service 내부에 다음과 같이 로직을 삽입하였다. 
```java
    @RequestMapping(value = "/payments/approve",
            method = RequestMethod.POST,
            produces = "application/json;charset=UTF-8")
    public PaymentResult approve(@RequestBody HashMap<String, String> map) {

        PaymentResult pr = new PaymentResult();
        try {
            String callId    = this.getParam(map, "callId", true);

            // Circuit break 테스트를 위해 일부러 sleep을 발생시킨다.
            if (callId.equals ("999")) {
                System.out.println("<<<<< --- SLEEPING (1±0.2 seconds) for Hystrix Test --- >>>>> ");
                Thread.sleep((long) (800 + Math.random() * 400));
                ...
            }
```
   
- 다음은 Payment를 호출하는 Caller 서비스의 설정 파일에 timeout 임계값을 1.5초로 설정한다.
```yml
# application.yml (Caller 서비스)
feign:
  hystrix:
    enabled: true
hystrix:
  command:
    default:
      execution.isolation.thread.timeoutInMilliseconds: 1500
      circuitBreaker.requestVolumeThreshold: 1

```
   
- 이제 siege 툴을 사용하여 부하를 줄 경우 어떻게 CB가 작동되는지 확한다.
  
```shell
# (1) 5명 동시 사용자로, 10초 동안 부하를 발생시킨 경우
siege -c5 -t10S -v 'http://localhost:8081/calls/payCall/999'
```
  ![](/images/cal262-hystrix-ex1.png)
_(Availability가 100%로, 서비스 응답시 1.2초의 sleep이 발생하더라도 임게값이 1.5초이기 때문에 CB가 작동되지 않는다.)_


```shell
# (2) 30명 동시 사용자로, 10초 동안 부하를 발생시킨 경우
siege -c30 -t10S -v 'http://localhost:8081/calls/payCall/999'
```
![](/images/cal262-hystrix-ex2.png)
_(Availability가 현저하게 낮아지며, CB가 발동되어 에러를 리턴하였다.)_
   

## 오토스케일 아웃 (HPA)

- 앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 

- 콜요청(caller) 서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 10개까지 늘려준다.

- 기 배포된 deployment의 Resources의 설정값이 높아 아래 내용으로 변경
```yaml
    resources:
      requests:
        memory: "64Mi"
        cpu: "100m"
      limits:
        memory: "500Mi"
        cpu: "200m"
```
- HPA 설정 값 확인 및 HPA 배포
```
 - cpu-percent=15
 - Min Pod : 1
 - Max Pod : 10
```
```shell
kubectl autoscale deployment caller --cpu-percent=15 --min=1 --max=10 -n nicecall
```
![hpa-caller](https://user-images.githubusercontent.com/45417337/133004629-cacc1f37-71cc-4aa6-8bd5-6e35a1964e6d.PNG)

- Siege를 이용해 부하 발생   
![hpa-siege부하](https://user-images.githubusercontent.com/45417337/133004680-a39e25b2-67e8-45c4-9fe4-35aaaf859552.PNG)

- 부하 발생 후 Pod 상태 확인
![hpa-결과](https://user-images.githubusercontent.com/45417337/133004691-8316f126-ac70-41ae-b3de-0f071dec51fa.PNG)

HPA 설정에 의해 Pod가 10까지 늘어난것을 확인

## Zero-downtime Deploy (Readiness Probe) 

* 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함.
- Readiness 미적용 yaml확인
```yaml
# deploy-readiness-v2.yaml
apiVersion : apps/v1
kind: Deployment
metadata:
  name: catcher
  namespace: nicecall
  labels:
    app: catcher
spec:
  replicas: 1
  selector:
    matchLabels:
      app: catcher
  template:
    metadata:
      labels:
        app: catcher
    spec:
      containers:
        - name: catcher
          image: kubecal262.azurecr.io/nicecall-catcher:v0.2
          ports:
            - containerPort: 8080
          env:
            - name: catcher-area
              valueFrom:
                configMapKeyRef:
                  name: catcher-cm
                  key: area
          resources:
            requests:
              memory: "64Mi"
              cpu: "200m"
            limits:
              memory: "500Mi"
              cpu: "500m"
          volumeMounts:
            - mountPath: "/mnt/azure"
              name: volume
      volumes:
        - name: volume
          persistentVolumeClaim:
            claimName: nicecall-disk
```

- Readiness 적용 deployment 배포
```shell
kubectl apply -f deploy-readiness-v2.yaml
```

- Readiness 미적용 배포에 대한 siege 워크로드 모니터링   
![readiness2](https://user-images.githubusercontent.com/45417337/133004511-167d6709-858a-480d-ada6-b0f6975212db.PNG)

- Readiness 적용 yaml확인
```yaml
# deploy-readiness-v3.yaml
apiVersion : apps/v1
kind: Deployment
metadata:
  name: catcher
  namespace: nicecall
  labels:
    app: catcher
spec:
  replicas: 1
  selector:
    matchLabels:
      app: catcher
  template:
    metadata:
      labels:
        app: catcher
    spec:
      containers:
        - name: catcher
          image: kubecal262.azurecr.io/nicecall-catcher:v0.3
          ports:
            - containerPort: 8080
          env:
            - name: catcher-area
              valueFrom:
                configMapKeyRef:
                  name: catcher-cm
                  key: area
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 90
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          resources:
            requests:
              memory: "64Mi"
              cpu: "200m"
            limits:
              memory: "500Mi"
              cpu: "500m"
          volumeMounts:
            - mountPath: "/mnt/azure"
              name: volume
      volumes:
        - name: volume
          persistentVolumeClaim:
            claimName: nicecall-disk
```

- Readiness 적용 deployment 배포
```shell
kubectl apply -f deploy-readiness-v3.yaml
```

- siege 워크로드로 부하 발생 및 모니터링   
![readiness4](https://user-images.githubusercontent.com/45417337/133004535-b2a944a3-3427-441c-a604-6a1446a4e55b.PNG)

Readiness 적용 deployment 배포 시 서비스 중단 없이 운영되는것을 확인

## Self-healing (Liveness Probe)

Liveness Probe의 기능인 Pod 상태 체크 후 Pod가 비정상적인 경우 자동 재시작 하는 기능을 확인한다.
- Liveness Probe 적용 yaml 확인
```yaml
# deploy-liveness.yaml
apiVersion : apps/v1
kind: Deployment
metadata:
  name: catcher
  namespace: nicecall
  labels:
    app: catcher
spec:
  replicas: 1
  selector:
    matchLabels:
      app: catcher
  template:
    metadata:
      labels:
        app: catcher
    spec:
      containers:
        - name: catcher
          image: kubecal262.azurecr.io/nicecall-catcher:v0.1
          ports:
            - containerPort: 8080
          env:
            - name: catcher-area
              valueFrom:
                configMapKeyRef:
                  name: catcher-cm
                  key: area
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 3
          resources:
            requests:
              memory: "64Mi"
              cpu: "200m"
            limits:
              memory: "500Mi"
              cpu: "500m"
          volumeMounts:
            - mountPath: "/mnt/azure"
              name: volume
      volumes:
        - name: volume
          persistentVolumeClaim:
            claimName: nicecall-disk
```

- 테스트 대상 Pod 상태 확인
![liveness1](https://user-images.githubusercontent.com/45417337/133004292-2f5a345d-0e9a-443e-8b48-098056f937f9.PNG)

- 대상 Pod의 상태를 불능(Down)으로 변경
![liveness2](https://user-images.githubusercontent.com/45417337/133004303-0725f961-c995-44fc-bb0f-5276880f11f4.PNG)

- Pod의 상태 변경 사항 확인 - Restarts 변경됨
![liveness3](https://user-images.githubusercontent.com/45417337/133004405-4eb1283f-4b2f-40d0-87ba-75f006de9512.PNG)

- Pod Event 확인
![liveness4](https://user-images.githubusercontent.com/45417337/133004422-6c314b5f-5954-46f0-a358-9dea33df11b7.PNG)

테스트를 통해 liveness Probe가 적용된 경우 Pod의 상태가 불능일 경우 재시작 됨을 확인
