import { useState, useEffect } from 'react';
import { fetchFeatureFlags } from '../api/client';

export function useFeatureFlag(flagName: string): boolean {
  const [isEnabled, setIsEnabled] = useState<boolean>(false);

  useEffect(() => {
    const checkFlag = async () => {
      try {
        const features = await fetchFeatureFlags();
        if (features && typeof features[flagName] === 'boolean') {
          setIsEnabled(features[flagName]);
        }
      } catch (error) {
        console.error('Failed to fetch feature flags:', error);
      }
    };

    checkFlag();
  }, [flagName]);

  return isEnabled;
}
