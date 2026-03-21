import React from 'react';
import { useAuth } from '../contexts/AuthContext';
import { LoginButton } from '../components/LoginButton';
import { DocumentTextIcon, SparklesIcon, ChevronRightIcon } from '@heroicons/react/24/solid';

export const LandingPage: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  // If authenticated, redirect to dashboard (handled by App.tsx)
  if (isAuthenticated) {
    return null;
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100">
      {/* Hero Section */}
      <div className="container mx-auto px-4 py-16">
        <div className="text-center max-w-3xl mx-auto">
          {/* Logo/Icon */}
          <div className="mb-8 flex justify-center">
            <img
              src="/bubblesnotes.png"
              alt="BubblesNotes Logo"
              className="h-16 w-16 rounded-full object-cover shadow-lg"
            />
          </div>

          {/* Main Heading */}
          <h1 className="text-5xl font-bold text-gray-900 mb-6">
            BubblesNotes
          </h1>
          
          {/* Tagline */}
          <p className="text-xl text-gray-600 mb-8">
            Capture your thoughts, organize your ideas, and enhance them with AI.
            A modern note-taking app designed for simplicity and power.
          </p>

          {/* CTA Button */}
          <div className="mb-12 flex justify-center">
            <LoginButton />
          </div>

          {/* Feature Highlights */}
          <div className="grid md:grid-cols-3 gap-8 mt-16">
            {/* Feature 1 */}
            <div className="bg-white rounded-xl shadow-md p-6 hover:shadow-lg transition-shadow">
              <div className="bg-blue-100 rounded-full w-12 h-12 flex items-center justify-center mb-4 mx-auto">
                <DocumentTextIcon className="h-6 w-6 text-blue-600" />
              </div>
              <h3 className="text-lg font-semibold text-gray-900 mb-2">Quick Notes</h3>
              <p className="text-gray-600">
                Create notes instantly with a persistent editor at your fingertips.
              </p>
            </div>

            {/* Feature 2 */}
            <div className="bg-white rounded-xl shadow-md p-6 hover:shadow-lg transition-shadow">
              <div className="bg-green-100 rounded-full w-12 h-12 flex items-center justify-center mb-4 mx-auto">
                <svg className="h-6 w-6 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
              </div>
              <h3 className="text-lg font-semibold text-gray-900 mb-2">Markdown Support</h3>
              <p className="text-gray-600">
                Write in Markdown with real-time preview and formatting.
              </p>
            </div>

            {/* Feature 3 */}
            <div className="bg-white rounded-xl shadow-md p-6 hover:shadow-lg transition-shadow">
              <div className="bg-purple-100 rounded-full w-12 h-12 flex items-center justify-center mb-4 mx-auto">
                <SparklesIcon className="h-6 w-6 text-purple-600" />
              </div>
              <h3 className="text-lg font-semibold text-gray-900 mb-2">AI Enhancement</h3>
              <p className="text-gray-600">
                Let AI generate titles, summaries, and tag suggestions automatically.
              </p>
            </div>
          </div>

          {/* Additional Features */}
          <div className="mt-12 text-center">
            <p className="text-gray-500 mb-4">And much more...</p>
            <ul className="text-left max-w-md mx-auto space-y-2">
              <li className="flex items-center text-gray-600">
                <ChevronRightIcon className="h-5 w-5 text-blue-600 mr-2" />
                File attachments with encryption
              </li>
              <li className="flex items-center text-gray-600">
                <ChevronRightIcon className="h-5 w-5 text-blue-600 mr-2" />
                Tag-based organization
              </li>
              <li className="flex items-center text-gray-600">
                <ChevronRightIcon className="h-5 w-5 text-blue-600 mr-2" />
                Full-text search
              </li>
              <li className="flex items-center text-gray-600">
                <ChevronRightIcon className="h-5 w-5 text-blue-600 mr-2" />
                Infinite scroll for seamless browsing
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
};
