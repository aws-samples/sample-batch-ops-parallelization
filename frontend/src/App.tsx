import { useState } from 'react'
import { BrowserRouter, Routes, Route, useNavigate, useLocation, useSearchParams } from 'react-router-dom'
import './App.css'
import { WorkflowForm, ViewWorkflow, ListWorkflows } from './components'
import s3Logo from './assets/s3.svg'

import AppLayout from '@cloudscape-design/components/app-layout'
import Header from '@cloudscape-design/components/header'
import ContentLayout from '@cloudscape-design/components/content-layout'
import SideNavigation from '@cloudscape-design/components/side-navigation'
import TopNavigation from '@cloudscape-design/components/top-navigation'
import type { SideNavigationProps } from '@cloudscape-design/components/side-navigation'
import { Flashbar, type FlashbarProps } from '@cloudscape-design/components'

interface ViewWorkflowsContentProps {
  addNotification?: (notification: FlashbarProps.MessageDefinition) => void;
  clearNotifications?: () => void;
}

function ViewWorkflowsContent({ addNotification, clearNotifications }: ViewWorkflowsContentProps) {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  
  const namespaceID = searchParams.get('namespaceID')
  const workflowName = searchParams.get('workflowName')

  const setSelectedWorkflow = (namespaceID?: string, workflowName?: string) => {
    if (namespaceID && workflowName) {
      navigate(`/workflows/view?namespaceID=${namespaceID}&workflowName=${workflowName}`)
    } else {
      navigate('/workflows/view')
    }
  }

  return (
    <ContentLayout
      header={
        <Header
          variant="h1"
          description="View details of workflows"
        >
          View Workflows
        </Header>
      }
    >
      {(namespaceID && workflowName) ? (
        <ViewWorkflow 
          namespaceID={namespaceID} 
          workflowName={workflowName}
          onBack={() => setSelectedWorkflow()}
          addNotification={addNotification}
          clearNotifications={clearNotifications}
        />
      ) : (
        <ListWorkflows 
          onViewWorkflow={setSelectedWorkflow}
          addNotification={addNotification}
          clearNotifications={clearNotifications}
        />
      )}
    </ContentLayout>
  )
}

function AppContent() {
  const navigate = useNavigate()
  const location = useLocation()
  
  const [navigationOpen, setNavigationOpen] = useState(true)
  const [notifications, setNotifications] = useState<FlashbarProps.MessageDefinition[]>([])

  const navItems: SideNavigationProps['items'] = [
    {
      type: 'section',
      text: 'Workflows',
      items: [
        { type: 'link', text: 'Create Workflow', href: '/workflows/create' },
        { type: 'link', text: 'View Workflows', href: '/workflows/view' }
      ]
    }
  ]

  const addNotification = (notification: FlashbarProps.MessageDefinition) => {
    setNotifications(prev => [...prev, notification])
  }

  const clearNotifications = () => {
    setNotifications([])
  }

  return (
    <>
      <TopNavigation
        identity={{
          href: "/",
          title: "S3 Replicator",
          logo: {
            src: s3Logo,
            alt: "S3 Replicator"
          }
        }}
        utilities={[
          {
            type: "button",
            text: "Documentation",
            href: "/docs",
            external: true,
            externalIconAriaLabel: " (opens in a new tab)"
          },
          {
            type: "menu-dropdown",
            text: "Account",
            description: "user@example.com",
            iconName: "user-profile",
            items: [
              { id: "signout", text: "Sign out" }
            ]
          }
        ]}
      />
      <AppLayout
        navigation={
          <SideNavigation
            activeHref={location.pathname}
            header={{ text: 'Navigation', href: '/' }}
            items={navItems}
            onFollow={event => {
              if (!event.detail.external) {
                event.preventDefault()
                navigate(event.detail.href)
              }
            }}
          />
        }
        navigationOpen={navigationOpen}
        onNavigationChange={({ detail }) => setNavigationOpen(detail.open)}
        content={
          <Routes>
            <Route 
              path="/workflows/create" 
              element={
                <ContentLayout
                  header={
                    <Header
                      variant="h1"
                      description="Create a new workflow to transfer data between S3 buckets"
                    >
                      Create Workflow
                    </Header>
                  }
                >
                  <WorkflowForm 
                    addNotification={addNotification}
                    clearNotifications={clearNotifications}
                  />
                </ContentLayout>
              } 
            />
            <Route 
              path="/workflows/view" 
              element={
                <ViewWorkflowsContent 
                  addNotification={addNotification}
                  clearNotifications={clearNotifications}
                />
              } 
            />
            <Route 
              path="/" 
              element={
                <ContentLayout
                  header={
                    <Header
                      variant="h1"
                      description="Welcome to the S3 Replicator"
                    >
                      Dashboard
                    </Header>
                  }
                >
                  <div>Select an option from the sidebar to get started.</div>
                </ContentLayout>
              } 
            />
          </Routes>
        }
        toolsHide={true}
        contentType="form"
        navigationWidth={300}
        notifications={
          notifications.length > 0 ? (
            <Flashbar 
              items={notifications.map(item => ({
                ...item,
                onDismiss: () => {
                  setNotifications(prev => prev.filter(n => n.id !== item.id));
                }
              }))} 
            />
          ) : undefined
        }
      />
    </>
  )
}

function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  )
}

export default App
