/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.request;

import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.TestFor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 05/04/2016
 */
public class UserRequestTest extends BaseFinderTest<UserGroup> {
  private UserRequest myRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myRequest = new UserRequest();
    myRequest.initForTests(BaseFinderTest.getBeanContext(myFixture));
  }


  @Test
  void testBasic1() throws Throwable {
    final SUser user1 = createUser("user1");
    final SUser user2 = createUser("user2");

    SecurityContextImpl securityContext = new SecurityContextImpl();

    user2.addRole(RoleScope.globalScope(), getProjectAdminRole());

    securityContext.runAs(user2, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        assertNotNull(myRequest.serveUser("username:user1", Fields.LONG.getFieldsSpec()));
        assertNotNull(myRequest.serveUsers(null, Fields.LONG.getFieldsSpec()));
        assertEquals(1, myRequest.getGroups("username:user1", Fields.LONG.getFieldsSpec()).groups.size());
        assertNotNull(myRequest.serveUserProperties("username:user1", Fields.LONG.getFieldsSpec()));
      }
    });

    securityContext.runAs(user1, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        assertNotNull(myRequest.serveUser("username:user1", Fields.LONG.getFieldsSpec()));
        assertEquals(1, myRequest.getGroups("username:user1", Fields.LONG.getFieldsSpec()).groups.size());
        assertNotNull(myRequest.serveUserProperties("username:user1", Fields.LONG.getFieldsSpec()));
      }
    });

    securityContext.runAs(user1, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        checkException(AuthorizationFailedException.class, new Runnable() {
          @Override
          public void run() {
            myRequest.serveUser("username:user2", Fields.LONG.getFieldsSpec());
          }
        }, null);
      }
    });

    securityContext.runAs(user1, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        checkException(AuthorizationFailedException.class, new Runnable() {
          @Override
          public void run() {
            myRequest.getGroups("username:user2", Fields.LONG.getFieldsSpec());
          }
        }, null);
      }
    });

    securityContext.runAs(user1, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        checkException(AuthorizationFailedException.class, new Runnable() {
          @Override
          public void run() {
            myRequest.serveUserProperties("username:user2", Fields.LONG.getFieldsSpec());
          }
        }, null);
      }
    });

    securityContext.runAs(user1, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        checkException(AuthorizationFailedException.class, new Runnable() {
          @Override
          public void run() {
            myRequest.serveUsers(null, Fields.LONG.getFieldsSpec());
          }
        }, null);
      }
    });

  }

  @Test
  @TestFor(issues = {"TW-44842"})
  void testUnauthorizedUsersList() throws Throwable {
    final SUser user1 = createUser("user1");
    final SUser user2 = createUser("user2");

    SecurityContextImpl securityContext = new SecurityContextImpl();

    user2.addRole(RoleScope.globalScope(), getProjectAdminRole());

    securityContext.runAs(user2, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        User result = myRequest.serveUser("username:user1", "$long,groups(group(users(user)))");
        assertNotNull(result);
        assertNotNull(result.getGroups());
        assertNotNull(result.getGroups().groups);
        assertEquals(1, result.getGroups().groups.size());
        assertNotNull(result.getGroups().groups.get(0).users);
        assertNotNull(result.getGroups().groups.get(0).users.users);
        assertEquals(2, result.getGroups().groups.get(0).users.users.size());
      }
    });

    securityContext.runAs(user1, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        User result = myRequest.serveUser("username:user1", "$long,groups(group)");
        assertNotNull(result);
        assertNotNull(result.getGroups());
        assertNotNull(result.getGroups().groups);
        assertEquals(1, result.getGroups().groups.size());
      }
    });

    securityContext.runAs(user1, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        User result = myRequest.serveUser("username:user1", "$long,groups(group(users(user)))");
        assertNotNull(result);
        assertNotNull(result.getGroups());
        assertNotNull(result.getGroups().groups);
        assertEquals(1, result.getGroups().groups.size());
        assertNull(result.getGroups().groups.get(0).users); //on getting users, AuthorizationFailedException is thrown so users are not included
      }
    });
  }
}
