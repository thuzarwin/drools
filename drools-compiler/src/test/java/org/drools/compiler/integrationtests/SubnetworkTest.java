/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.compiler.integrationtests;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.drools.compiler.CommonTestMethodBase;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.definition.type.Role;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.Agenda;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.utils.KieHelper;

import static org.junit.Assert.assertEquals;

public class SubnetworkTest extends CommonTestMethodBase {

    @Test
    public void testNPEOnFlushingOfUnlinkedPmem() {
        // DROOLS-1285
        final String drl =
                "import " + A.class.getCanonicalName() + "\n" +
                "import " + B.class.getCanonicalName() + "\n" +
                "import " + C.class.getCanonicalName() + "\n" +
                "rule R1 when\n" +
                "    A()\n" +
                "    B()\n" +
                "    not( B() and C() )\n" +
                "then end\n";

        final KieSession kSession = new KieHelper().addContent( drl, ResourceType.DRL )
                                             .build( EventProcessingOption.STREAM )
                                             .newKieSession();

        final FactHandle fhA = kSession.insert( new A() );
        kSession.insert(new C());
        kSession.fireAllRules();

        kSession.delete( fhA );

        kSession.insert(new A());
        kSession.insert(new B());
        kSession.fireAllRules();
    }

    @Role(Role.Type.EVENT)
    public static class A { }

    @Role(Role.Type.EVENT)
    public static class B { }

    @Role(Role.Type.EVENT)
    public static class C { }

    @Test
    public void testRightStagingOnSharedSubnetwork() {
        // RHBRMS-2624
        final String drl =
                "import " + AtomicInteger.class.getCanonicalName() + ";\n" +
                "rule R1y when\n" +
                "    AtomicInteger() \n" +
                "    Number() from accumulate ( AtomicInteger( ) and $s : String( ) ; count($s) )" +
                "    Long()\n" +
                "then\n" +
                "    System.out.println(\"R1\");" +
                "end\n" +
                "\n" +
                "rule R1x when\n" +
                "    AtomicInteger() \n" +
                "    Number() from accumulate ( AtomicInteger( ) and $s : String( ) ; count($s) )\n" +
                "then\n" +
                "    System.out.println(\"R1\");" +
                "end\n" +
                "" +
                "rule R2 when\n" +
                "    $i : AtomicInteger( get() < 3 )\n" +
                "then\n" +
                "    System.out.println(\"R2\");" +
                "    $i.incrementAndGet();" +
                "    update($i);" +
                "end\n";

        final KieSession kieSession = new KieHelper().addContent( drl, ResourceType.DRL )
                                               .build().newKieSession();

        kieSession.insert( new AtomicInteger( 0 ) );
        kieSession.insert( "test" );

        kieSession.fireAllRules();
    }

    @Test(timeout = 10000L)
    public void testUpdateOnSharedSubnetwork() {
        // DROOLS-1360
        final String drl =
                "import " + AtomicInteger.class.getCanonicalName() + ";\n" +
                "global java.util.List list;\n" +
                "rule R1y when\n" +
                "    AtomicInteger() \n" +
                "    Number() from accumulate ( AtomicInteger( ) and $s : String( ) ; count($s) )" +
                "    eval(false)\n" +
                "then\n" +
                "end\n" +
                "\n" +
                "rule R2 when\n" +
                "    $i : AtomicInteger( get() < 3 )\n" +
                "then\n" +
                "    $i.incrementAndGet();" +
                "    insert(\"test\" + $i.get());" +
                "    update($i);" +
                "end\n" +
                "\n" +
                "rule R1x when\n" +
                "    AtomicInteger() \n" +
                "    $c : Number() from accumulate ( AtomicInteger( ) and $s : String( ) ; count($s) )\n" +
                "    eval(true)\n" +
                "then\n" +
                "    list.add($c);" +
                "end\n";

        final KieSession kieSession = new KieHelper().addContent( drl, ResourceType.DRL )
                                               .build().newKieSession();

        final List<Number> list = new ArrayList<Number>();
        kieSession.setGlobal( "list", list );

        kieSession.insert( new AtomicInteger( 0 ) );
        kieSession.insert( "test" );

        kieSession.fireAllRules();

        assertEquals(1, list.size());
        assertEquals(4, list.get(0).intValue());
    }

    @Test
    public void testSubNetworks() throws Exception {
        final KieBase kbase = SerializationHelper.serializeObject(loadKnowledgeBase("test_SubNetworks.drl"));
        final KieSession session = createKnowledgeSession(kbase);
    }

    @Test
    public void testSubnetworkSharingWith2Sinks() {
        // DROOLS-1656
        String drl1 =
                "import " + X.class.getCanonicalName() + "\n" +
                "import " + Y.class.getCanonicalName() + "\n" +
                "global java.util.List list" +
                "\n" +
                "rule R1 agenda-group \"G2\" when\n" +
                "    Number( intValue == 0 ) from accumulate (\n" +
                "        X( $id : id )\n" +
                "        and $y : Y( parentId == $id )\n" +
                "    ;count($y))\n" +
                "then\n" +
                "    list.add(\"R1\");\n" +
                "end\n" +
                "\n" +
                "rule R2 agenda-group \"G1\" when\n" +
                "    Number( intValue < 1 ) from accumulate (\n" +
                "        X( $id : id )\n" +
                "        and $y : Y( parentId == $id )\n" +
                "    ;count($y))\n" +
                "then\n" +
                "    list.add(\"R2\");\n" +
                "end\n" +
                "\n" +
                "rule R3 agenda-group \"G1\" no-loop when\n" +
                "    $x : X( $id : id )\n" +
                "then\n" +
                "    modify($x) { setId($id + 1) };\n" +
                "end\n";

        KieSession kieSession = new KieHelper()
                .addContent( drl1, ResourceType.DRL )
                .build().newKieSession();

        List<String> list = new ArrayList<>();
        kieSession.setGlobal( "list", list );

        kieSession.insert( new X(1) );
        kieSession.insert( new Y(1) );

        // DROOLS-2258
        kieSession.insert( new X(3) );
        kieSession.insert( new Y(3) );

        final Agenda agenda = kieSession.getAgenda();
        agenda.getAgendaGroup("G2").setFocus();
        agenda.getAgendaGroup("G1").setFocus();

        kieSession.fireAllRules();

        assertEquals( 2, list.size() );
        assertEquals( "R2", list.get( 0) );
        assertEquals( "R1", list.get( 1) );
    }

    public static class X {
        private int id;

        public X( int id ) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public void setId( int id ) {
            this.id = id;
        }
    }

    public static class Y {
        private final int parentId;

        public Y( int parentId ) {
            this.parentId = parentId;
        }

        public int getParentId() {
            return parentId;
        }
    }
}
